package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClienteFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClientesRespuesta;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla.OrdenPermitido;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidoFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductoFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.TiendaOpcion;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.UsuarioFila;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.UsuariosRespuesta;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Usuario;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    // El tamaño de página (20 por defecto, máximo 100) lo decide PeticionTabla, igual en todas las tablas

    private final MongoTemplate mongo;

    public PlataformaConsultasService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ─── Usuario: lo que tiene en curso (antes de desactivarlo) ─────────────

    /** Pedidos de una tienda o solicitudes de un taller que siguen en curso; lo avisa el modal de desactivar. */
    public record EnCurso(String tipo, String negocio, long pedidos, long solicitudes) {}

    static final java.util.List<String> PEDIDOS_EN_CURSO = java.util.List.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION,
            PedidoTienda.LISTO_PARA_RETIRAR, PedidoTienda.DESPACHADO);
    static final java.util.List<String> ORDENES_CERRADAS = java.util.List.of(
            com.drivemaster.drivemaster.model.EstadoOrden.ENTREGADA, com.drivemaster.drivemaster.model.EstadoOrden.RECHAZADA,
            com.drivemaster.drivemaster.model.EstadoOrden.CANCELADA);

    public EnCurso enCurso(String usuarioId) {
        Tienda tienda = mongo.findOne(Query.query(Criteria.where("usuarioId").is(usuarioId)), Tienda.class);
        if (tienda != null) {
            long pedidos = mongo.count(Query.query(Criteria.where("tiendaId").is(tienda.getId()).and("estado").in(PEDIDOS_EN_CURSO)),
                    PedidoTienda.class);
            return new EnCurso("TIENDA", tienda.getNombre(), pedidos, 0);
        }
        com.drivemaster.drivemaster.model.Taller taller = mongo.findOne(Query.query(Criteria.where("usuarioId").is(usuarioId)),
                com.drivemaster.drivemaster.model.Taller.class);
        if (taller != null) {
            long solicitudes = mongo.count(Query.query(Criteria.where("tallerId").is(taller.getId()).and("estado").nin(ORDENES_CERRADAS)),
                    OrdenServicio.class);
            return new EnCurso("TALLER", taller.getNombre(), 0, solicitudes);
        }
        return new EnCurso(null, null, 0, 0);
    }

    // ─── Orden permitido por tabla (lista blanca de sort) ───────────────────

    /** Productos: por defecto los más nuevos; también por nombre, precio, stock y fecha de ingreso. */
    public static final OrdenPermitido ORDEN_PRODUCTOS = OrdenPermitido.de(Sort.by(Sort.Direction.DESC, "id"),
            "fecha", "_id", "nombre", "nombre", "precio", "precioVenta", "stock", "stockActual");
    /** Clientes: por defecto los más nuevos; también por nombre y fecha de registro. */
    public static final OrdenPermitido ORDEN_CLIENTES = OrdenPermitido.de(Sort.by(Sort.Direction.DESC, "id"),
            "fecha", "fechaCreacion", "nombre", "nombre");
    /** Usuarios: por defecto los más nuevos; también por nombre, rol, fecha de registro y último ingreso. */
    public static final OrdenPermitido ORDEN_USUARIOS = OrdenPermitido.de(Sort.by(Sort.Direction.DESC, "id"),
            "fecha", "fechaCreacion", "nombre", "nombre", "rol", "rol", "ultimoAcceso", "ultimoLogin");
    /** Pedidos y envíos: por defecto del más reciente; también por fecha de pago, estado y tienda. */
    public static final OrdenPermitido ORDEN_PEDIDOS = OrdenPermitido.de(Sort.by(Sort.Direction.DESC, "fechaCreacion"),
            "fecha", "fechaCreacion", "estado", "estado", "tienda", "tiendaNombre");

    // ─── Productos ───────────────────────────────────────────────────────────

    /**
     * @param tiendaId solo los de esa tienda (vacío = todas)
     * @param nuevosDias solo los ingresados en los últimos N días (7 o 30; otro valor o null = todos)
     * @param estado "activos", "inactivos", "sin-stock" (vacío = todos)
     */
    public ProductosRespuesta productos(String q, String tiendaId, Integer nuevosDias, String estado, int pagina, int tamano) {
        return productos(q, tiendaId, nuevosDias, estado, PeticionTabla.de(pagina, tamano, null, null, null, ORDEN_PRODUCTOS));
    }

    public ProductosRespuesta productos(String q, String tiendaId, Integer nuevosDias, String estado, PeticionTabla tabla) {
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

        long total = mongo.count(Query.query(criterio), Producto.class);
        List<Producto> productos = mongo.find(Query.query(criterio).with(tabla.pageRequest(total)), Producto.class);

        Map<String, String> nombres = nombresDeTiendas(productos.stream().map(Producto::getTiendaId).toList());
        // Un producto "activo" de una tienda en pausa, rechazada o archivada no se vende: se muestra como Tienda inactiva
        java.util.Set<String> operativas = mongo.findAll(Tienda.class).stream()
                .filter(t -> EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()) && Boolean.TRUE.equals(t.getActivo()))
                .map(Tienda::getId).collect(Collectors.toSet());
        List<ProductoFila> filas = productos.stream().map(p -> new ProductoFila(p.getId(), p.getCodigo(), p.getNombre(),
                p.getCategoria(), p.getMarca(), p.getTipo(), p.getTiendaId(), nombres.get(p.getTiendaId()),
                p.getStockActual(), p.getPrecioVenta(), !Boolean.FALSE.equals(p.getActivo()), p.getImagenUrl(),
                fechaIngreso(p), operativas.contains(p.getTiendaId()))).toList();

        List<TiendaOpcion> tiendas = mongo.findAll(Tienda.class).stream()
                .filter(t -> !EstadoAprobacion.estaArchivado(t.getEstadoAprobacion())) // las archivadas no salen en los filtros
                .map(t -> new TiendaOpcion(t.getId(), t.getNombre()))
                .sorted(Comparator.comparing(t -> t.nombre() == null ? "" : t.nombre().toLowerCase())).toList();
        return new ProductosRespuesta(tabla.pagina(filas, total), tiendas);
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
        return clientes(q, ciudad, estado, PeticionTabla.de(pagina, tamano, null, null, null, ORDEN_CLIENTES));
    }

    public ClientesRespuesta clientes(String q, String ciudad, String estado, PeticionTabla tabla) {
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

        long total = mongo.count(Query.query(criterio), Usuario.class);
        List<Usuario> usuarios = mongo.find(Query.query(criterio).with(tabla.pageRequest(total)), Usuario.class);

        List<String> ids = usuarios.stream().map(Usuario::getId).toList();
        Map<String, Long> compras = conteo(CompraCatalogo.class, "clienteId", ids);
        Map<String, Long> servicios = conteo(OrdenServicio.class, "usuarioId", ids);

        List<ClienteFila> filas = usuarios.stream().map(u -> new ClienteFila(u.getId(), u.getNombre(),
                enmascarar(u.getCorreo()), u.getCiudad(), !Boolean.FALSE.equals(u.getActivo()),
                u.getFechaCreacion() != null ? LocalDateTime.ofInstant(u.getFechaCreacion(), ZoneId.systemDefault()) : null,
                compras.getOrDefault(u.getId(), 0L), servicios.getOrDefault(u.getId(), 0L))).toList();

        List<String> ciudades = mongo.findDistinct(Query.query(base), "ciudad", Usuario.class, String.class).stream()
                .filter(c -> c != null && !c.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new ClientesRespuesta(tabla.pagina(filas, total), ciudades);
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

    // ─── Usuarios del sistema ────────────────────────────────────────────────

    /** Todos los roles. VENDEDOR ya no se crea, pero las cuentas que quedan se siguen listando para poder reasignarlas. */
    static final Set<String> ROLES_USUARIOS = Set.of("SUPERADMIN", "ADMIN", "VENDEDOR", "TIENDA", "TALLER", "MECANICO", "CLIENTE");

    /**
     * Lista de todos los usuarios (cualquier rol) con filtros por rol y estado y búsqueda, con el correo enmascarado. Cambiar el rol o
     * activar o desactivar sigue siendo solo del SUPERADMIN (PUT /api/usuarios/{id}).
     *
     * @param rol uno de ROLES_USUARIOS (vacío = todos); un rol desconocido no devuelve nada
     * @param estado "activos" o "inactivos" (vacío = todos)
     */
    public UsuariosRespuesta usuarios(String q, String rol, String estado, int pagina, int tamano) {
        return usuarios(q, rol, estado, PeticionTabla.de(pagina, tamano, null, null, null, ORDEN_USUARIOS));
    }

    public UsuariosRespuesta usuarios(String q, String rol, String estado, PeticionTabla tabla) {
        List<Criteria> y = new ArrayList<>();
        if (hay(rol)) {
            String r = rol.trim().toUpperCase();
            y.add(ROLES_USUARIOS.contains(r) ? Criteria.where("rol").is(r) : Criteria.where("rol").is("__ninguno__"));
        } else {
            y.add(Criteria.where("rol").in(ROLES_USUARIOS));
        }
        if ("activos".equals(estado)) y.add(Criteria.where("activo").ne(false));
        if ("inactivos".equals(estado)) y.add(Criteria.where("activo").is(false));
        if (hay(q)) {
            Pattern p = coincide(q);
            y.add(new Criteria().orOperator(Criteria.where("nombre").regex(p), Criteria.where("correo").regex(p)));
        }
        Criteria criterio = new Criteria().andOperator(y.toArray(new Criteria[0]));

        long total = mongo.count(Query.query(criterio), Usuario.class);
        List<UsuarioFila> filas = mongo.find(Query.query(criterio)
                .with(tabla.pageRequest(total)), Usuario.class).stream()
                .map(u -> new UsuarioFila(u.getId(), u.getNombre(), enmascarar(u.getCorreo()), u.getRol(),
                        !Boolean.FALSE.equals(u.getActivo()), Boolean.TRUE.equals(u.getBloqueado()),
                        local(u.getFechaCreacion()), local(u.getUltimoLogin())))
                .toList();

        Map<String, Long> porRol = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("rol").in(ROLES_USUARIOS)),
                Aggregation.group("rol").count().as("n")), Usuario.class, Document.class).getMappedResults()) {
            Object n = d.get("n");
            porRol.put(String.valueOf(d.get("_id")), n instanceof Number num ? num.longValue() : 0L);
        }
        return new UsuariosRespuesta(tabla.pagina(filas, total), porRol);
    }

    private static LocalDateTime local(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneId.systemDefault());
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
     * @param alerta ATRASADO, POR_VENCER o SIN_RETIRAR (el semáforo de SemaforoPedido); vacío = sin filtro
     */
    public PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, boolean soloAtrasados,
                                    String alerta, int pagina, int tamano) {
        return pedidos(estado, tiendaId, entrega, soloAtrasados ? ATRASADO : alerta, pagina, tamano, LocalDateTime.now());
    }

    public PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, boolean soloAtrasados,
                                    int pagina, int tamano) {
        return pedidos(estado, tiendaId, entrega, soloAtrasados, null, pagina, tamano);
    }

    PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, boolean soloAtrasados,
                             int pagina, int tamano, LocalDateTime ahora) {
        return pedidos(estado, tiendaId, entrega, soloAtrasados ? ATRASADO : null, pagina, tamano, ahora);
    }

    static final String ATRASADO = "ATRASADO";
    static final String POR_VENCER = "POR_VENCER";
    static final String SIN_RETIRAR = "SIN_RETIRAR";

    PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, String alerta,
                             int pagina, int tamano, LocalDateTime ahora) {
        return pedidos(estado, tiendaId, entrega, alerta, RangoFechas.SIN_RANGO,
                PeticionTabla.de(pagina, tamano, null, null, null, ORDEN_PEDIDOS), ahora);
    }

    /** @param rango desde/hasta sobre la fecha de pago (fechaCreacion del pedido), en hora de Colombia */
    public PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, String alerta, RangoFechas rango,
                                    PeticionTabla tabla) {
        return pedidos(estado, tiendaId, entrega, alerta, rango, tabla, LocalDateTime.now());
    }

    PedidosRespuesta pedidos(String estado, String tiendaId, String entrega, String alerta, RangoFechas rango,
                             PeticionTabla tabla, LocalDateTime ahora) {
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
        if (hay(alerta)) {
            switch (alerta.trim().toUpperCase()) {
                case ATRASADO -> y.add(SemaforoPedido.despachoVencido(ahora));
                case POR_VENCER -> y.add(SemaforoPedido.despachoPorVencer(ahora));
                case SIN_RETIRAR -> y.add(SemaforoPedido.sinRetirar(ahora));
                default -> y.add(Criteria.where("estado").is("__ninguno__")); // una alerta inventada no devuelve nada
            }
        }
        if (rango != null && !rango.vacio()) y.add(rango.criterio("fechaCreacion"));
        Criteria criterio = y.isEmpty() ? new Criteria() : new Criteria().andOperator(y.toArray(new Criteria[0]));

        long total = mongo.count(Query.query(criterio), PedidoTienda.class);
        List<PedidoTienda> pedidos = mongo.find(Query.query(criterio).with(tabla.pageRequest(total)), PedidoTienda.class);
        List<PedidoFila> filas = pedidos.stream().map(p -> fila(p, ahora)).toList();

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(Aggregation.group("estado").count().as("n")),
                "pedidos_tienda", Document.class).getMappedResults()) {
            Object n = d.get("n");
            porEstado.put(String.valueOf(d.get("_id")), n instanceof Number num ? num.longValue() : 0L);
        }
        long atrasados = mongo.count(Query.query(SemaforoPedido.despachoVencido(ahora)), PedidoTienda.class);
        long porVencer = mongo.count(Query.query(SemaforoPedido.despachoPorVencer(ahora)), PedidoTienda.class);
        long sinRetirar = mongo.count(Query.query(SemaforoPedido.sinRetirar(ahora)), PedidoTienda.class);
        long problemas = mongo.count(Query.query(problemaAbierto()), PedidoTienda.class);
        List<TiendaOpcion> tiendas = mongo.findAll(Tienda.class).stream()
                .filter(t -> !EstadoAprobacion.estaArchivado(t.getEstadoAprobacion())) // las archivadas no salen en los filtros
                .map(t -> new TiendaOpcion(t.getId(), t.getNombre()))
                .sorted(Comparator.comparing(t -> t.nombre() == null ? "" : t.nombre().toLowerCase())).toList();
        return new PedidosRespuesta(tabla.pagina(filas, total), porEstado, atrasados, problemas, tiendas,
                porVencer, sinRetirar);
    }

    private static Criteria problemaAbierto() {
        return Criteria.where("problemaReportado").is(true).and("resolucionProblema").is(null);
    }

    static PedidoFila fila(PedidoTienda p, LocalDateTime ahora) {
        boolean catalogo = p.esCatalogo();
        String semaforoDespacho = SemaforoPedido.despacho(p, ahora);
        String semaforoRetiro = SemaforoPedido.retiro(p, ahora);
        boolean atrasado = SemaforoPedido.ROJO.equals(semaforoDespacho);
        long dias = p.getFechaCreacion() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(p.getFechaCreacion().toLocalDate(), ahora.toLocalDate()));
        int articulos = p.getItems() == null ? 0 : p.getItems().stream()
                .mapToInt(i -> i.getCantidad() != null ? i.getCantidad() : 0).sum();
        return new PedidoFila(p.getId(), catalogo ? PedidoTienda.ORIGEN_CATALOGO : PedidoTienda.ORIGEN_TALLER, p.getEstado(),
                p.getTiendaId(), p.getTiendaNombre(), catalogo ? p.getEntrega() : null, destino(p, catalogo), articulos, dias,
                p.getFechaCreacion(), p.getPlazoDespachoHasta(), atrasado,
                Boolean.TRUE.equals(p.getProblemaReportado()) && p.getResolucionProblema() == null,
                semaforoDespacho, semaforoRetiro, semaforoRetiro == null ? null : RetiroPendienteService.diasSinRetirar(p, ahora));
    }

    /** Solo la ciudad: la dirección y el nombre del cliente no hacen falta para vigilar el avance. */
    private static String destino(PedidoTienda p, boolean catalogo) {
        if (!catalogo) {
            return com.drivemaster.drivemaster.util.NombreNegocio.conPrefijo("Taller", p.getTallerNombre())
                    + (p.getTallerCiudad() != null ? " · " + p.getTallerCiudad() : "");
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
}
