package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.Pagina;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegocioFila;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegocioPendiente;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegociosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.PlanActual;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.PlanSuscripcion;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.repository.mysql.PlanSuscripcionRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tiendas y talleres unidos con las aprobaciones. Solo lectura. La vista completa (plan, calificación, productos u
 * órdenes) es solo del SUPERADMIN; el ADMIN usa {@link #pendientes()}. El acceso lo define SecurityConfig.
 */
@Service
public class PlataformaNegociosService {

    static final String TIENDA = "TIENDA";
    static final String TALLER = "TALLER";
    static final int TAMANO_MAXIMO = 50;
    static final int TAMANO_POR_DEFECTO = 20;
    private static final Set<String> ESTADOS = Set.of(EstadoAprobacion.PENDIENTE, EstadoAprobacion.APROBADO, EstadoAprobacion.RECHAZADO);

    private final MongoTemplate mongo;
    private final SuscripcionRepository suscripcionRepository;
    private final PlanSuscripcionRepository planRepository;

    public PlataformaNegociosService(MongoTemplate mongo, SuscripcionRepository suscripcionRepository,
                                     PlanSuscripcionRepository planRepository) {
        this.mongo = mongo;
        this.suscripcionRepository = suscripcionRepository;
        this.planRepository = planRepository;
    }

    // ─── Vista completa (SUPERADMIN) ─────────────────────────────────────────

    /**
     * @param tipo TIENDA o TALLER (vacío = ambos)
     * @param estado PENDIENTE, APROBADO o RECHAZADO (vacío = todos); las cuentas anteriores al marketplace cuentan como aprobadas
     */
    public NegociosRespuesta negocios(String tipo, String estado, String q, int pagina, int tamano) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        String e = estado == null ? "" : estado.trim().toUpperCase();
        boolean conTiendas = t.isEmpty() || TIENDA.equals(t);
        boolean conTalleres = t.isEmpty() || TALLER.equals(t);
        if (!e.isEmpty() && !ESTADOS.contains(e)) {
            return new NegociosRespuesta(new Pagina<>(List.of(), 0, 0, tamano(tamano)), conteos());
        }

        Map<String, PlanActual> planes = planesActivos();
        List<NegocioFila> filas = new ArrayList<>();
        if (conTiendas) {
            List<Tienda> tiendas = mongo.find(Query.query(filtro(new Criteria(), e, q, "ciudad")), Tienda.class);
            Map<String, Long> total = conteoPor(Producto.class, "tiendaId", null);
            Map<String, Long> activos = conteoPor(Producto.class, "tiendaId", Criteria.where("activo").ne(false));
            for (Tienda x : tiendas) {
                filas.add(new NegocioFila(TIENDA, x.getId(), x.getNombre(), x.getCiudad(), estadoDe(x.getEstadoAprobacion()),
                        !Boolean.FALSE.equals(x.getActivo()), x.getFechaRegistro(), x.getMotivoRechazo(),
                        planes.get(TIENDA + ":" + x.getId()), null, 0,
                        total.getOrDefault(x.getId(), 0L), activos.getOrDefault(x.getId(), 0L), null, null,
                        faltantesTienda(x)));
            }
        }
        if (conTalleres) {
            List<Taller> talleres = mongo.find(Query.query(filtro(new Criteria(), e, q, "ciudad")), Taller.class);
            Map<String, long[]> ordenes = ordenesPorTaller();
            Map<String, double[]> notas = calificacionesPorTaller();
            for (Taller x : talleres) {
                long[] o = ordenes.getOrDefault(x.getId(), new long[2]);
                double[] n = notas.get(x.getId());
                Double calificacion = n != null ? n[0] : x.getRating();
                filas.add(new NegocioFila(TALLER, x.getId(), x.getNombre(), x.getCiudad(), estadoDe(x.getEstadoAprobacion()),
                        !Boolean.FALSE.equals(x.getActivo()), x.getFechaRegistro(), x.getMotivoRechazo(),
                        planes.get(TALLER + ":" + x.getId()), calificacion, n != null ? (long) n[1] : 0,
                        null, null, o[0], o[1], faltantesTaller(x)));
            }
        }

        // Los más recientes primero; los pendientes siempre arriba para que se vean
        filas.sort(Comparator.comparing((NegocioFila f) -> !EstadoAprobacion.PENDIENTE.equals(f.estado()))
                .thenComparing(Comparator.comparing(NegocioFila::registrado, Comparator.nullsLast(Comparator.reverseOrder()))));

        int tam = tamano(tamano);
        int pag = Math.max(0, pagina);
        int desde = Math.min(filas.size(), pag * tam);
        List<NegocioFila> pagina1 = filas.subList(desde, Math.min(filas.size(), desde + tam));
        return new NegociosRespuesta(new Pagina<>(List.copyOf(pagina1), filas.size(), pag, tam), conteos());
    }

    // ─── Pendientes (ADMIN y SUPERADMIN) ─────────────────────────────────────

    /** Negocios por revisar, con lo mínimo para decidir. El plan, la calificación y la actividad no se incluyen. */
    public List<NegocioPendiente> pendientes() {
        List<NegocioPendiente> r = new ArrayList<>();
        Criteria pendiente = Criteria.where("estadoAprobacion").is(EstadoAprobacion.PENDIENTE);
        for (Tienda x : mongo.find(Query.query(new Criteria().andOperator(new Criteria(), pendiente)), Tienda.class)) {
            r.add(new NegocioPendiente(TIENDA, x.getId(), x.getNombre(), x.getCiudad(), x.getDireccion(), x.getNit(),
                    x.getFechaRegistro(), faltantesTienda(x)));
        }
        for (Taller x : mongo.find(Query.query(pendiente), Taller.class)) {
            r.add(new NegocioPendiente(TALLER, x.getId(), x.getNombre(), x.getCiudad(), x.getDireccion(), null,
                    x.getFechaRegistro(), faltantesTaller(x)));
        }
        r.sort(Comparator.comparing(NegocioPendiente::registrado, Comparator.nullsLast(Comparator.reverseOrder())));
        return r;
    }

    // ─── Datos por completar ─────────────────────────────────────────────────

    static List<String> faltantesTienda(Tienda t) {
        List<String> f = new ArrayList<>();
        if (vacio(t.getDireccion())) f.add("dirección");
        if (vacio(t.getCiudad())) f.add("ciudad");
        if (vacio(t.getHorario())) f.add("horario");
        return f;
    }

    static List<String> faltantesTaller(Taller t) {
        List<String> f = new ArrayList<>();
        if (vacio(t.getDireccion())) f.add("dirección");
        if (vacio(t.getCiudad())) f.add("ciudad");
        if (vacio(t.getTelefono())) f.add("teléfono");
        if (vacio(t.getHorario())) f.add("horario");
        return f;
    }

    // ─── Consultas ───────────────────────────────────────────────────────────

    private static String estadoDe(String guardado) {
        return guardado == null ? EstadoAprobacion.APROBADO : guardado;
    }

    private Criteria filtro(Criteria base, String estado, String q, String campoCiudad) {
        List<Criteria> y = new ArrayList<>();
        y.add(base);
        if (EstadoAprobacion.APROBADO.equals(estado)) {
            // Las cuentas anteriores al marketplace no tienen estado y se consideran aprobadas
            y.add(new Criteria().orOperator(Criteria.where("estadoAprobacion").is(null),
                    Criteria.where("estadoAprobacion").is(EstadoAprobacion.APROBADO)));
        } else if (!estado.isEmpty()) {
            y.add(Criteria.where("estadoAprobacion").is(estado));
        }
        if (q != null && !q.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
            y.add(new Criteria().orOperator(Criteria.where("nombre").regex(p), Criteria.where(campoCiudad).regex(p)));
        }
        return new Criteria().andOperator(y.toArray(new Criteria[0]));
    }

    /** Tiendas y talleres por estado (para las pestañas), sin contar la tienda de la plataforma. */
    private Map<String, Long> conteos() {
        Map<String, Long> r = new LinkedHashMap<>();
        for (String e : List.of(EstadoAprobacion.PENDIENTE, EstadoAprobacion.APROBADO, EstadoAprobacion.RECHAZADO)) {
            r.put(e, mongo.count(Query.query(filtro(new Criteria(), e, null, "ciudad")), Tienda.class)
                    + mongo.count(Query.query(filtro(new Criteria(), e, null, "ciudad")), Taller.class));
        }
        return r;
    }

    private Map<String, PlanActual> planesActivos() {
        Map<String, String> nombres = planRepository.findAll().stream()
                .collect(Collectors.toMap(PlanSuscripcion::getCodigo, p -> p.getNombre() != null ? p.getNombre() : p.getCodigo(), (a, b) -> a));
        Map<String, PlanActual> r = new HashMap<>();
        for (Suscripcion s : suscripcionRepository.findByEstado(Suscripcion.ACTIVA)) {
            r.put(s.getActorTipo() + ":" + s.getActorId(),
                    new PlanActual(s.getPlanCodigo(), nombres.getOrDefault(s.getPlanCodigo(), s.getPlanCodigo()), s.getFechaFin()));
        }
        return r;
    }

    private Map<String, Long> conteoPor(Class<?> coleccion, String campo, Criteria filtro) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> pasos = new ArrayList<>();
        if (filtro != null) pasos.add(Aggregation.match(filtro));
        pasos.add(Aggregation.group(campo).count().as("n"));
        Map<String, Long> r = new HashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(pasos), coleccion, Document.class).getMappedResults()) {
            if (d.get("_id") != null) r.put(String.valueOf(d.get("_id")), d.get("n") instanceof Number n ? n.longValue() : 0L);
        }
        return r;
    }

    /** [total de órdenes, entregadas] por taller. */
    private Map<String, long[]> ordenesPorTaller() {
        Map<String, long[]> r = new HashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("flujo").is(EstadoOrden.FLUJO_MARKETPLACE).and("tallerId").ne(null)),
                Aggregation.group("tallerId", "estado").count().as("n")), OrdenServicio.class, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            long n = d.get("n") instanceof Number num ? num.longValue() : 0L;
            long[] c = r.computeIfAbsent(String.valueOf(id.get("tallerId")), k -> new long[2]);
            c[0] += n;
            if (EstadoOrden.ENTREGADA.equals(id.get("estado"))) c[1] += n;
        }
        return r;
    }

    /** [promedio, cantidad] de las calificaciones que los clientes dieron a cada taller. */
    private Map<String, double[]> calificacionesPorTaller() {
        Map<String, double[]> r = new HashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tallerId").ne(null).and("calificacionTaller").ne(null)),
                Aggregation.group("tallerId").avg("calificacionTaller").as("promedio").count().as("n")),
                OrdenServicio.class, Document.class).getMappedResults()) {
            double promedio = d.get("promedio") instanceof Number p ? Math.round(p.doubleValue() * 10) / 10.0 : 0;
            r.put(String.valueOf(d.get("_id")), new double[]{promedio, d.get("n") instanceof Number n ? n.doubleValue() : 0});
        }
        return r;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static int tamano(int pedido) {
        return pedido <= 0 ? TAMANO_POR_DEFECTO : Math.min(pedido, TAMANO_MAXIMO);
    }
}
