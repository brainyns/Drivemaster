package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClienteFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClientesRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.Pagina;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidoFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductoFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.TiendaOpcion;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Productos y clientes para el panel de la plataforma: solo lectura, filtrados y paginados en el servidor.
 * El acceso lo restringe SecurityConfig (/api/plataforma/**: solo SUPERADMIN).
 */
@Service
public class PlataformaConsultasService {

    static final int TAMANO_MAXIMO = 50;
    static final int TAMANO_POR_DEFECTO = 20;

    private final MongoTemplate mongo;

    public PlataformaConsultasService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ─── Productos ───────────────────────────────────────────────────────────

    /**
     * @param tiendaId solo los de esa tienda (vacío = todas)
     * @param nuevosDias solo los ingresados en los últimos N días (7 o 30; otro valor o null = todos)
     * @param estado "activos", "inactivos", "sin-stock" (vacío = todos)
     */
    public ProductosRespuesta productos(String q, String tiendaId, Integer nuevosDias, String estado, int pagina, int tamano) {
        Criteria criterio = new Criteria();
        List<Criteria> y = new ArrayList<>();
        if (hay(tiendaId)) y.add(Criteria.where("tiendaId").is(tiendaId.trim()));
        if (nuevosDias != null && nuevosDias > 0) {
            Date desde = Date.from(LocalDateTime.now().minusDays(nuevosDias).atZone(ZoneId.systemDefault()).toInstant());
            y.add(Criteria.where("_id").gte(new ObjectId(desde)));
        }
        if ("activos".equals(estado)) y.add(Criteria.where("activo").ne(false));
        if ("inactivos".equals(estado)) y.add(Criteria.where("activo").is(false));
        if ("sin-stock".equals(estado)) {
            y.add(Criteria.where("tipo").ne("ENCARGO"));
            y.add(new Criteria().orOperator(Criteria.where("stockActual").is(null), Criteria.where("stockActual").lte(0)));
        }
        if (hay(q)) {
            Pattern p = coincide(q);
            y.add(new Criteria().orOperator(Criteria.where("nombre").regex(p), Criteria.where("codigo").regex(p),
                    Criteria.where("marca").regex(p), Criteria.where("referencia").regex(p)));
        }
        if (!y.isEmpty()) criterio = new Criteria().andOperator(y.toArray(new Criteria[0]));

        int tam = tamano(tamano);
        int pag = Math.max(0, pagina);
        long total = mongo.count(Query.query(criterio), Producto.class);
        List<Producto> productos = mongo.find(Query.query(criterio).with(PageRequest.of(pag, tam, Sort.by(Sort.Direction.DESC, "id"))),
                Producto.class);

        Map<String, String> nombres = nombresDeTiendas(productos.stream().map(Producto::getTiendaId).toList());
        List<ProductoFila> filas = productos.stream().map(p -> new ProductoFila(p.getId(), p.getCodigo(), p.getNombre(),
                p.getCategoria(), p.getMarca(), p.getTipo(), p.getTiendaId(), nombres.get(p.getTiendaId()),
                p.getStockActual(), p.getPrecioVenta(), !Boolean.FALSE.equals(p.getActivo()), p.getImagenUrl(),
                fechaIngreso(p))).toList();

        List<TiendaOpcion> tiendas = mongo.findAll(Tienda.class).stream()
                .map(t -> new TiendaOpcion(t.getId(), t.getNombre()))
                .sorted(Comparator.comparing(t -> t.nombre() == null ? "" : t.nombre().toLowerCase())).toList();
        return new ProductosRespuesta(new Pagina<>(filas, total, pag, tam), tiendas);
    }

    /** Cuándo ingresó: el campo fechaCreacion o, en los anteriores a él, el momento en que Mongo creó el documento. */
    static LocalDateTime fechaIngreso(Producto p) {
        if (p.getFechaCreacion() != null) return p.getFechaCreacion();
        if (p.getId() != null && ObjectId.isValid(p.getId())) {
            return new ObjectId(p.getId()).getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }

    private Map<String, String> nombresDeTiendas(List<String> ids) {
        List<String> distintos = ids.stream().filter(i -> i != null).distinct().toList();
        if (distintos.isEmpty()) return Map.of();
        return mongo.find(Query.query(Criteria.where("id").in(distintos)), Tienda.class).stream()
                .collect(Collectors.toMap(Tienda::getId, t -> t.getNombre() != null ? t.getNombre() : "", (a, b) -> a));
    }

    // ─── Clientes ────────────────────────────────────────────────────────────

    /**
     * @param ciudad solo esa ciudad (vacío = todas)
     * @param estado "activos" o "inactivos" (vacío = todos)
     */
    public ClientesRespuesta clientes(String q, String ciudad, String estado, int pagina, int tamano) {
        Criteria base = Criteria.where("rol").is("CLIENTE");
        List<Criteria> y = new ArrayList<>();
        y.add(base);
        if (hay(ciudad)) y.add(Criteria.where("ciudad").is(ciudad.trim()));
        if ("activos".equals(estado)) y.add(Criteria.where("activo").ne(false));
        if ("inactivos".equals(estado)) y.add(Criteria.where("activo").is(false));
        if (hay(q)) {
            Pattern p = coincide(q);
            y.add(new Criteria().orOperator(Criteria.where("nombre").regex(p), Criteria.where("correo").regex(p)));
        }
        Criteria criterio = new Criteria().andOperator(y.toArray(new Criteria[0]));

        int tam = tamano(tamano);
        int pag = Math.max(0, pagina);
        long total = mongo.count(Query.query(criterio), Usuario.class);
        List<Usuario> usuarios = mongo.find(Query.query(criterio).with(PageRequest.of(pag, tam, Sort.by(Sort.Direction.DESC, "id"))),
                Usuario.class);

        List<String> ids = usuarios.stream().map(Usuario::getId).toList();
        Map<String, Long> compras = conteo(CompraCatalogo.class, "clienteId", ids);
        Map<String, Long> servicios = conteo(OrdenServicio.class, "usuarioId", ids);

        List<ClienteFila> filas = usuarios.stream().map(u -> new ClienteFila(u.getId(), u.getNombre(),
                enmascarar(u.getCorreo()), u.getCiudad(), !Boolean.FALSE.equals(u.getActivo()),
                u.getFechaCreacion() != null ? LocalDateTime.ofInstant(u.getFechaCreacion(), ZoneId.systemDefault()) : null,
                compras.getOrDefault(u.getId(), 0L), servicios.getOrDefault(u.getId(), 0L))).toList();

        List<String> ciudades = mongo.findDistinct(Query.query(base), "ciudad", Usuario.class, String.class).stream()
                .filter(c -> c != null && !c.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new ClientesRespuesta(new Pagina<>(filas, total, pag, tam), ciudades);
    }

    /** Cuántos documentos tiene cada cliente de la página en una colección (una sola consulta agrupada). */
    private Map<String, Long> conteo(Class<?> coleccion, String campo, List<String> clienteIds) {
        if (clienteIds.isEmpty()) return Map.of();
        Map<String, Long> r = new HashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where(campo).in(clienteIds)),
                Aggregation.group(campo).count().as("n")), coleccion, Document.class).getMappedResults()) {
            Object n = d.get("n");
            r.put(String.valueOf(d.get("_id")), n instanceof Number num ? num.longValue() : 0L);
        }
        return r;
    }

    // ─── Pedidos y envíos ────────────────────────────────────────────────────

    static final String PROBLEMA = "PROBLEMA";
    private static final Set<String> ESTADOS_PEDIDO = Set.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION,
            PedidoTienda.DESPACHADO, PedidoTienda.NO_DESPACHADO, PedidoTienda.RECIBIDO, PedidoTienda.LISTO_PARA_RETIRAR,
            PedidoTienda.RETIRADO, PedidoTienda.ENTREGADO, PedidoTienda.NO_ENTREGADO);

    /**
     * Monitor de lo que se vende en las tiendas y de los envíos y retiros en curso. Lo ve el ADMIN y el SUPERADMIN.
     *
     * @param estado un estado del pedido o "PROBLEMA" (problema de envío abierto); vacío = todos
     * @param entrega ENVIO o RETIRO (vacío = ambos)
     * @param soloAtrasados solo los que la tienda no despachó dentro del plazo
     */
    public PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, boolean soloAtrasados,
                                    int pagina, int tamano) {
        return pedidos(estado, tiendaId, entrega, soloAtrasados, pagina, tamano, LocalDateTime.now());
    }

    PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, boolean soloAtrasados,
                             int pagina, int tamano, LocalDateTime ahora) {
        List<Criteria> y = new ArrayList<>();
        if (hay(estado)) {
            String e = estado.trim().toUpperCase();
            if (PROBLEMA.equals(e)) y.add(problemaAbierto());
            else if (ESTADOS_PEDIDO.contains(e)) y.add(Criteria.where("estado").is(e));
            else y.add(Criteria.where("estado").is("__ninguno__")); // un estado inventado no devuelve nada
        }
        if (hay(tiendaId)) y.add(Criteria.where("tiendaId").is(tiendaId.trim()));
        if (PedidoTienda.ENVIO.equalsIgnoreCase(entrega)) y.add(Criteria.where("entrega").is(PedidoTienda.ENVIO));
        if (PedidoTienda.RETIRO.equalsIgnoreCase(entrega)) y.add(Criteria.where("entrega").is(PedidoTienda.RETIRO));
        if (soloAtrasados) y.add(atrasado(ahora));
        Criteria criterio = y.isEmpty() ? new Criteria() : new Criteria().andOperator(y.toArray(new Criteria[0]));

        int tam = tamano(tamano);
        int pag = Math.max(0, pagina);
        long total = mongo.count(Query.query(criterio), PedidoTienda.class);
        List<PedidoTienda> pedidos = mongo.find(Query.query(criterio)
                .with(PageRequest.of(pag, tam, Sort.by(Sort.Direction.DESC, "fechaCreacion"))), PedidoTienda.class);
        List<PedidoFila> filas = pedidos.stream().map(p -> fila(p, ahora)).toList();

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(Aggregation.group("estado").count().as("n")),
                "pedidos_tienda", Document.class).getMappedResults()) {
            Object n = d.get("n");
            porEstado.put(String.valueOf(d.get("_id")), n instanceof Number num ? num.longValue() : 0L);
        }
        long atrasados = mongo.count(Query.query(atrasado(ahora)), PedidoTienda.class);
        long problemas = mongo.count(Query.query(problemaAbierto()), PedidoTienda.class);
        List<TiendaOpcion> tiendas = mongo.findAll(Tienda.class).stream()
                .map(t -> new TiendaOpcion(t.getId(), t.getNombre()))
                .sorted(Comparator.comparing(t -> t.nombre() == null ? "" : t.nombre().toLowerCase())).toList();
        return new PedidosRespuesta(new Pagina<>(filas, total, pag, tam), porEstado, atrasados, problemas, tiendas);
    }

    private static Criteria atrasado(LocalDateTime ahora) {
        return Criteria.where("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION)
                .and("plazoDespachoHasta").lt(ahora);
    }

    private static Criteria problemaAbierto() {
        return Criteria.where("problemaReportado").is(true).and("resolucionProblema").is(null);
    }

    static PedidoFila fila(PedidoTienda p, LocalDateTime ahora) {
        boolean catalogo = p.esCatalogo();
        boolean enEspera = PedidoTienda.PENDIENTE.equals(p.getEstado()) || PedidoTienda.EN_PREPARACION.equals(p.getEstado());
        boolean atrasado = enEspera && p.getPlazoDespachoHasta() != null && p.getPlazoDespachoHasta().isBefore(ahora);
        long dias = p.getFechaCreacion() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(p.getFechaCreacion().toLocalDate(), ahora.toLocalDate()));
        int articulos = p.getItems() == null ? 0 : p.getItems().stream()
                .mapToInt(i -> i.getCantidad() != null ? i.getCantidad() : 0).sum();
        return new PedidoFila(p.getId(), catalogo ? PedidoTienda.ORIGEN_CATALOGO : PedidoTienda.ORIGEN_TALLER, p.getEstado(),
                p.getTiendaId(), p.getTiendaNombre(), catalogo ? p.getEntrega() : null, destino(p, catalogo), articulos, dias,
                p.getFechaCreacion(), p.getPlazoDespachoHasta(), atrasado,
                Boolean.TRUE.equals(p.getProblemaReportado()) && p.getResolucionProblema() == null);
    }

    /** Solo la ciudad: la dirección y el nombre del cliente no hacen falta para vigilar el avance. */
    private static String destino(PedidoTienda p, boolean catalogo) {
        if (!catalogo) {
            return "Taller " + (p.getTallerNombre() != null ? p.getTallerNombre() : "") + (p.getTallerCiudad() != null ? " · " + p.getTallerCiudad() : "");
        }
        if (PedidoTienda.ENVIO.equals(p.getEntrega())) return p.getEnvioCiudad();
        return "Retiro en tienda" + (p.getTiendaCiudad() != null ? " · " + p.getTiendaCiudad() : "");
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /** "ana.perez@gmail.com" → "an***@gmail.com" */
    static String enmascarar(String correo) {
        if (correo == null || !correo.contains("@")) return null;
        int at = correo.indexOf('@');
        String local = correo.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + correo.substring(at);
    }

    private static boolean hay(String s) {
        return s != null && !s.isBlank();
    }

    /** Búsqueda literal (sin interpretar el texto como expresión regular), sin distinguir mayúsculas. */
    private static Pattern coincide(String q) {
        return Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
    }

    private static int tamano(int pedido) {
        if (pedido <= 0) return TAMANO_POR_DEFECTO;
        return Math.min(pedido, TAMANO_MAXIMO);
    }
}
