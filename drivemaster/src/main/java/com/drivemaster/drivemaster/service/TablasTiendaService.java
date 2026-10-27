package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla.OrdenPermitido;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO.Inventario;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO.Movimientos;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO.Pedidos;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO.ResumenInventario;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO.ResumenMovimientos;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.mongodb.client.model.Collation;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Las tablas paginadas del panel de la tienda (Inventario, Pedidos y Movimientos): filtro, búsqueda, orden y página
 * en el servidor, sobre todos los registros de la tienda. Son las mismas reglas que antes aplicaba el navegador
 * (productosTienda.js, pedidosTienda.js e inventarioTienda.js); los contadores y los indicadores siguen contando todo.
 */
@Service
public class TablasTiendaService {

    // ─── Orden permitido por tabla ──────────────────────────────────────────

    /** Inventario: por defecto lo que pide atención primero (agotados y poco stock) y luego por nombre. */
    static final OrdenPermitido ORDEN_INVENTARIO = OrdenPermitido.de(
            Sort.by(Sort.Order.asc("_peso"), Sort.Order.asc("nombre"), Sort.Order.desc("_id")),
            "nombre", "nombre", "stock", "stockActual", "precio", "precioVenta", "estado", "_peso");

    /** Pedidos: por defecto la urgencia (ver urgencia); también por fecha, total y estado. */
    static final OrdenPermitido ORDEN_PEDIDOS = OrdenPermitido.de(Sort.unsorted(),
            "fecha", "fechaCreacion", "total", "total", "estado", "estado");

    /** Movimientos: por defecto del más reciente al más antiguo. */
    static final OrdenPermitido ORDEN_MOVIMIENTOS = OrdenPermitido.de(
            Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("_id")),
            "fecha", "fecha", "tipo", "tipo", "cantidad", "cantidad");

    public static PeticionTabla peticionInventario(Integer page, Integer size, String sort) {
        return PeticionTabla.de(page, size, null, null, sort, ORDEN_INVENTARIO);
    }

    public static PeticionTabla peticionPedidos(Integer page, Integer size, String sort) {
        return PeticionTabla.de(page, size, null, null, sort, ORDEN_PEDIDOS);
    }

    public static PeticionTabla peticionMovimientos(Integer page, Integer size, String sort) {
        return PeticionTabla.de(page, size, null, null, sort, ORDEN_MOVIMIENTOS);
    }

    private static final Collation ESPANOL = Collation.builder().locale("es").build();

    private final MongoTemplate mongo;

    public TablasTiendaService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ─── Inventario ─────────────────────────────────────────────────────────

    static final List<String> FILTROS_INVENTARIO = List.of("TODOS", "POCO", "AGOTADOS", "PUBLICADOS", "OCULTOS");

    /**
     * @param filtro TODOS, POCO (en el mínimo o por debajo), AGOTADOS, PUBLICADOS u OCULTOS
     * @param busqueda nombre, código, categoría, marca o referencia (sin distinguir mayúsculas)
     */
    public Inventario inventario(String tiendaId, String filtro, String busqueda, PeticionTabla t) {
        String f = filtro == null || filtro.isBlank() ? "TODOS" : filtro.trim().toUpperCase(Locale.ROOT);
        if (!FILTROS_INVENTARIO.contains(f)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro desconocido: " + filtro);
        }
        List<Document> base = new ArrayList<>();
        base.add(new Document("$match", busquedaInventario(tiendaId, busqueda)));
        base.add(new Document("$addFields", new Document("_nivel", nivelStock()).append("_peso", pesoNivel())));
        Document porFiltro = filtroInventario(f);
        if (porFiltro != null) base.add(new Document("$match", porFiltro));

        List<Document> conteo = new ArrayList<>(base);
        conteo.add(new Document("$count", "n"));
        long total = primero(agregar("productos", conteo), "n");

        List<Document> pagina = new ArrayList<>(base);
        pagina.add(new Document("$sort", aDocumento(t.orden())));
        pagina.add(new Document("$skip", (long) t.paginaPara(total) * t.tamano()));
        pagina.add(new Document("$limit", t.tamano()));
        List<Producto> productos = agregar("productos", pagina).stream()
                .map(d -> mongo.getConverter().read(Producto.class, d)).toList();

        return new Inventario(t.pagina(productos, total), conteoInventario(tiendaId), resumenInventario(tiendaId));
    }

    private static Document busquedaInventario(String tiendaId, String busqueda) {
        Document match = new Document("tiendaId", tiendaId);
        if (busqueda != null && !busqueda.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(busqueda.trim()), Pattern.CASE_INSENSITIVE);
            match.append("$or", Stream.of("nombre", "codigo", "categoria", "marca", "referencia")
                    .map(c -> new Document(c, p)).toList());
        }
        return match;
    }

    private static Document filtroInventario(String filtro) {
        return switch (filtro) {
            case "POCO" -> new Document("_nivel", "bajo");
            case "AGOTADOS" -> new Document("_nivel", "agotado");
            case "PUBLICADOS" -> new Document("activo", new Document("$ne", false));
            case "OCULTOS" -> new Document("activo", false);
            default -> null;
        };
    }

    /**
     * El nivel de stock de productosTienda.js (nivelStock): encargo | agotado | bajo (en el mínimo o por debajo) |
     * cerca (hasta el mínimo + max(2, mitad del mínimo)) | ok. Sin mínimo solo se distingue el agotado.
     */
    static Document nivelStock() {
        Object stock = new Document("$ifNull", List.of("$stockActual", 0));
        Object margen = new Document("$max", List.of(2, new Document("$ceil", new Document("$multiply", List.of("$stockMinimo", 0.5)))));
        return new Document("$switch", new Document("branches", List.of(
                rama(new Document("$eq", List.of("$tipo", "ENCARGO")), "encargo"),
                rama(new Document("$lte", List.of(stock, 0)), "agotado"),
                rama(new Document("$in", List.of(new Document("$type", "$stockMinimo"), List.of("missing", "null"))), "ok"),
                rama(new Document("$lte", List.of(stock, "$stockMinimo")), "bajo"),
                rama(new Document("$lte", List.of(stock, new Document("$add", List.of("$stockMinimo", margen)))), "cerca")))
                .append("default", "ok"));
    }

    /** El orden "lo que pide atención primero": agotado 0, bajo 1, cerca 2, ok y encargo 3. */
    static Document pesoNivel() {
        return new Document("$switch", new Document("branches", List.of(
                rama(new Document("$eq", List.of("$_nivel", "agotado")), 0),
                rama(new Document("$eq", List.of("$_nivel", "bajo")), 1),
                rama(new Document("$eq", List.of("$_nivel", "cerca")), 2)))
                .append("default", 3));
    }

    private static Document rama(Object caso, Object entonces) {
        return new Document("case", caso).append("then", entonces);
    }

    /** Productos por pestaña, de toda la tienda (sin búsqueda), como los contadores de antes. */
    private Map<String, Long> conteoInventario(String tiendaId) {
        Document g = new Document("_id", null)
                .append("TODOS", new Document("$sum", 1))
                .append("POCO", contarSi(new Document("$eq", List.of("$_nivel", "bajo"))))
                .append("AGOTADOS", contarSi(new Document("$eq", List.of("$_nivel", "agotado"))))
                .append("PUBLICADOS", contarSi(new Document("$ne", List.of("$activo", false))))
                .append("OCULTOS", contarSi(new Document("$eq", List.of("$activo", false))));
        Document r = unico(agregar("productos", List.of(
                new Document("$match", new Document("tiendaId", tiendaId)),
                new Document("$addFields", new Document("_nivel", nivelStock())),
                new Document("$group", g))));
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (String f : FILTROS_INVENTARIO) conteo.put(f, numero(r == null ? null : r.get(f)));
        return conteo;
    }

    /** Los indicadores de la tienda: total, publicados, los que piden atención y el valor del stock. */
    private ResumenInventario resumenInventario(String tiendaId) {
        Object stock = new Document("$max", List.of(0, new Document("$ifNull", List.of("$stockActual", 0))));
        Document g = new Document("_id", null)
                .append("total", new Document("$sum", 1))
                .append("publicados", contarSi(new Document("$ne", List.of("$activo", false))))
                .append("atencion", contarSi(new Document("$in", List.of("$_nivel", List.of("agotado", "bajo")))))
                .append("valor", new Document("$sum", new Document("$multiply", List.of(new Document("$ifNull", List.of("$precioVenta", 0)), stock))))
                .append("valorCosto", new Document("$sum", new Document("$multiply", List.of(new Document("$ifNull", List.of("$precioCompra", 0)), stock))));
        Document r = unico(agregar("productos", List.of(
                new Document("$match", new Document("tiendaId", tiendaId)),
                new Document("$addFields", new Document("_nivel", nivelStock())),
                new Document("$group", g))));
        if (r == null) return new ResumenInventario(0, 0, 0, 0, 0);
        return new ResumenInventario(numero(r.get("total")), numero(r.get("publicados")), numero(r.get("atencion")),
                decimal(r.get("valor")), decimal(r.get("valorCosto")));
    }

    // ─── Pedidos ────────────────────────────────────────────────────────────

    /** Las pestañas de PedidosTienda y los estados de cada una (TODOS: sin filtro). */
    static final Map<String, Set<String>> PESTANAS_PEDIDOS;

    static {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("ATENDER", Set.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION, PedidoTienda.LISTO_PARA_RETIRAR));
        m.put("PREPARACION", Set.of(PedidoTienda.EN_PREPARACION));
        m.put("LISTOS", Set.of(PedidoTienda.LISTO_PARA_RETIRAR));
        m.put("EN_CAMINO", Set.of(PedidoTienda.DESPACHADO));
        m.put("NUEVOS", Set.of(PedidoTienda.PENDIENTE));
        m.put("ENTREGADOS", Set.of(PedidoTienda.RECIBIDO, PedidoTienda.RETIRADO, PedidoTienda.ENTREGADO));
        m.put("CANCELADOS", Set.of(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO));
        m.put("TODOS", Set.of());
        PESTANAS_PEDIDOS = java.util.Collections.unmodifiableMap(m);
    }

    public Pedidos pedidos(String tiendaId, String pestana, String busqueda, RangoFechas rango, PeticionTabla t) {
        return pedidos(tiendaId, pestana, busqueda, rango, t, LocalDateTime.now());
    }

    /**
     * La base filtra por tienda, pestaña y rango de fechas (de pago). La búsqueda corre sobre lo que la tienda ve de
     * cada pedido (PedidoTiendaService.paraTienda): en un retiro solo el nombre y la inicial del apellido, así que
     * buscar un apellido completo no revela a quién es.
     */
    Pedidos pedidos(String tiendaId, String pestana, String busqueda, RangoFechas rango, PeticionTabla t, LocalDateTime ahora) {
        String p = pestana == null || pestana.isBlank() ? "TODOS" : pestana.trim().toUpperCase(Locale.ROOT);
        Set<String> estados = PESTANAS_PEDIDOS.get(p);
        if (estados == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pestaña desconocida: " + pestana);

        List<Criteria> y = new ArrayList<>();
        y.add(Criteria.where("tiendaId").is(tiendaId));
        if (!estados.isEmpty()) y.add(Criteria.where("estado").in(estados));
        if (rango != null && !rango.vacio()) y.add(rango.criterio("fechaCreacion"));
        Query q = Query.query(new Criteria().andOperator(y.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        Predicate<PedidoTienda> coincide = coincidePedido(busqueda);
        List<PedidoTienda> vistos = mongo.find(q, PedidoTienda.class).stream()
                .map(PedidoTiendaService::paraTienda).filter(coincide).sorted(ordenPedidos(t, ahora)).toList();

        return new Pedidos(t.paginar(vistos), conteoPedidos(tiendaId));
    }

    /** Lo mismo que coincide() de pedidosTienda.js: referencia (#ABC123), id, quien recibe, cliente, taller, vehículo y productos. */
    static Predicate<PedidoTienda> coincidePedido(String busqueda) {
        String q = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
        if (q.isEmpty()) return p -> true;
        return p -> {
            List<String> campos = new ArrayList<>();
            if (p.getId() != null) {
                campos.add(p.getId().substring(Math.max(0, p.getId().length() - 6)).toUpperCase(Locale.ROOT));
                campos.add(p.getId());
            }
            campos.add(p.getEnvioReceptor());
            campos.add(p.getClienteNombre());
            campos.add(p.getTallerNombre());
            campos.add(p.getVehiculoDescripcion());
            if (p.getItems() != null) p.getItems().forEach(i -> campos.add(i.getProductoNombre()));
            return campos.stream().anyMatch(c -> c != null && c.toLowerCase(Locale.ROOT).contains(q));
        };
    }

    /**
     * Sin sort, la urgencia de ordenarPorUrgencia (pedidosTienda.js): primero los de plazo vencido, luego por despachar
     * (por plazo), después los listos para retirar (el que más lleva esperando primero) y al final el resto (los más
     * recientes primero). Con sort, por ese campo y, si empatan, el más reciente.
     */
    static Comparator<PedidoTienda> ordenPedidos(PeticionTabla t, LocalDateTime ahora) {
        Comparator<PedidoTienda> desempate = Comparator.comparing(PedidoTienda::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        if (t.campoOrden() == null) return urgencia(ahora).thenComparing(desempate);
        Comparator<PedidoTienda> c = switch (t.campoOrden()) {
            case "fecha" -> Comparator.comparing(PedidoTienda::getFechaCreacion, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "total" -> Comparator.comparing((PedidoTienda p) -> p.getTotal() == null ? 0 : p.getTotal());
            default -> Comparator.comparing(PedidoTienda::getEstado, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        return (t.ascendente() ? c : c.reversed()).thenComparing(desempate);
    }

    static Comparator<PedidoTienda> urgencia(LocalDateTime ahora) {
        Function<PedidoTienda, Integer> grupo = p -> {
            boolean porDespachar = PedidoTienda.PENDIENTE.equals(p.getEstado()) || PedidoTienda.EN_PREPARACION.equals(p.getEstado());
            if (porDespachar && p.getPlazoDespachoHasta() != null && p.getPlazoDespachoHasta().isBefore(ahora)) return 0;
            if (porDespachar) return 1;
            return PedidoTienda.LISTO_PARA_RETIRAR.equals(p.getEstado()) ? 2 : 3;
        };
        return (a, b) -> {
            int g = Integer.compare(grupo.apply(a), grupo.apply(b));
            if (g != 0) return g;
            int ga = grupo.apply(a);
            if (ga <= 1) return Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())
                    .compare(a.getPlazoDespachoHasta(), b.getPlazoDespachoHasta());
            if (ga == 2) return Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())
                    .compare(a.getFechaListoRetiro(), b.getFechaListoRetiro());
            return Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()).compare(a.getFechaCreacion(), b.getFechaCreacion());
        };
    }

    /** Pedidos por pestaña, de toda la tienda (sin búsqueda ni fechas), como los contadores de antes. */
    private Map<String, Long> conteoPedidos(String tiendaId) {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (Document d : agregar("pedidos_tienda", List.of(
                new Document("$match", new Document("tiendaId", tiendaId)),
                new Document("$group", new Document("_id", "$estado").append("n", new Document("$sum", 1)))))) {
            porEstado.put(String.valueOf(d.get("_id")), numero(d.get("n")));
        }
        Map<String, Long> conteo = new LinkedHashMap<>();
        PESTANAS_PEDIDOS.forEach((pestana, estados) -> conteo.put(pestana, estados.isEmpty()
                ? porEstado.values().stream().mapToLong(Long::longValue).sum()
                : estados.stream().mapToLong(e -> porEstado.getOrDefault(e, 0L)).sum()));
        return conteo;
    }

    // ─── Movimientos ────────────────────────────────────────────────────────

    static final Set<String> TIPOS_MOVIMIENTO = Set.of("ENTRADA", "SALIDA", "AJUSTE");

    /**
     * @param busqueda nombre o código del producto (de esta tienda)
     * @param tipo ENTRADA, SALIDA o AJUSTE (vacío: todos)
     */
    public Movimientos movimientos(String tiendaId, String tipo, String busqueda, RangoFechas rango, PeticionTabla t) {
        List<Criteria> y = new ArrayList<>();
        y.add(Criteria.where("tiendaId").is(tiendaId));
        if (tipo != null && !tipo.isBlank()) {
            String tp = tipo.trim().toUpperCase(Locale.ROOT);
            if (!TIPOS_MOVIMIENTO.contains(tp)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo desconocido: " + tipo);
            y.add(Criteria.where("tipo").is(tp));
        }
        if (busqueda != null && !busqueda.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(busqueda.trim()), Pattern.CASE_INSENSITIVE);
            List<String> ids = mongo.find(Query.query(Criteria.where("tiendaId").is(tiendaId)
                    .orOperator(Criteria.where("nombre").regex(p), Criteria.where("codigo").regex(p))), Producto.class)
                    .stream().map(Producto::getId).toList();
            y.add(Criteria.where("productoId").in(ids));
        }
        if (rango != null && !rango.vacio()) y.add(rango.criterio("fecha"));
        Criteria criterio = new Criteria().andOperator(y.toArray(new Criteria[0]));

        long total = mongo.count(Query.query(criterio), MovimientoInventario.class);
        List<MovimientoInventario> items = mongo.find(Query.query(criterio).with(t.pageRequest(total)), MovimientoInventario.class);
        return new Movimientos(t.pagina(items, total), resumenMovimientos(tiendaId));
    }

    /** De todos los movimientos de la tienda, como el resumen de antes. */
    private ResumenMovimientos resumenMovimientos(String tiendaId) {
        long total = 0, entradas = 0, salidas = 0, ajustes = 0;
        for (Document d : agregar("movimientos_inventario", List.of(
                new Document("$match", new Document("tiendaId", tiendaId)),
                new Document("$group", new Document("_id", "$tipo").append("n", new Document("$sum", 1))
                        .append("unidades", new Document("$sum", new Document("$ifNull", List.of("$cantidad", 0)))))))) {
            long n = numero(d.get("n"));
            total += n;
            String tipo = String.valueOf(d.get("_id"));
            if ("ENTRADA".equals(tipo)) entradas = numero(d.get("unidades"));
            if ("SALIDA".equals(tipo)) salidas = numero(d.get("unidades"));
            if ("AJUSTE".equals(tipo)) ajustes = n;
        }
        return new ResumenMovimientos(total, entradas, salidas, ajustes);
    }

    // ─── Piezas ─────────────────────────────────────────────────────────────

    private List<Document> agregar(String coleccion, List<Document> etapas) {
        List<Document> r = new ArrayList<>();
        mongo.getCollection(coleccion).aggregate(etapas).collation(ESPANOL).into(r);
        return r;
    }

    private static Document contarSi(Document condicion) {
        return new Document("$sum", new Document("$cond", List.of(condicion, 1, 0)));
    }

    static Document aDocumento(Sort orden) {
        Document d = new Document();
        orden.forEach(o -> d.append(o.getProperty(), o.isAscending() ? 1 : -1));
        return d;
    }

    private static Document unico(List<Document> r) {
        return r.isEmpty() ? null : r.get(0);
    }

    private static long primero(List<Document> r, String campo) {
        return r.isEmpty() ? 0 : numero(r.get(0).get(campo));
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static double decimal(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
