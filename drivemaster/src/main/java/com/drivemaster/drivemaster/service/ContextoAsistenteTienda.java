package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Informes;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoBajo;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoVendido;
import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.DetalleCompra;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Tienda;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lo que el asistente de IA de la TIENDA puede ver: sus informes y, ya calculado aquí (el modelo no hace cuentas),
 * qué reponer, a quién le compró, qué pedidos esperan despacho, qué no se mueve y qué margen deja cada producto.
 * Todo filtrado por la tienda de la sesión y sin datos de clientes ni talleres.
 */
@Service
public class ContextoAsistenteTienda {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int DIAS_RITMO = 90;
    static final int DIAS_COBERTURA = 30;
    static final int MAX_LISTA = 15;
    static final int MAX_COMPRAS = 60;
    static final int MAX_PENDIENTES = 20;
    static final int MINIMO_POR_DEFECTO = InformesTiendaService.MINIMO_POR_DEFECTO;

    private final InformesTiendaService informes;
    private final CompraService compras;
    private final ProveedorService proveedores;
    private final MongoTemplate mongo;

    public ContextoAsistenteTienda(InformesTiendaService informes, CompraService compras, ProveedorService proveedores,
                                   MongoTemplate mongo) {
        this.informes = informes;
        this.compras = compras;
        this.proveedores = proveedores;
        this.mongo = mongo;
    }

    public record Reponer(String producto, String codigo, int stock, int minimo, long vendidas90d, long sugerido) {}
    public record SinMovimiento(String producto, String codigo, int stock, Double capitalInmovilizado, LocalDate ultimaVenta) {}
    public record Margen(String producto, double precioCompra, double precioVenta, double margenPct,
                         double gananciaPorUnidad, long vendidas90d, double ganancia90d) {}

    public Map<String, Object> contexto(Tienda tienda) {
        String id = tienda.getId();
        LocalDateTime ahora = LocalDateTime.now();
        Informes inf = informes.informes(id, 6);
        List<Producto> productos = mongo.find(Query.query(Criteria.where("tiendaId").is(id).and("activo").ne(false)), Producto.class);
        Map<String, Long> vendidas90 = unidadesVendidasDesde(id, ahora.minusDays(DIAS_RITMO));
        Map<String, LocalDate> ultimaVenta = ultimaVentaPorProducto(id);

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("tienda", tienda.getNombre());
        c.put("hoy", LocalDate.now(ZONA).toString());
        c.put("informes", inf);
        c.put("reponer", reponer(inf.stockBajo().items(), vendidas90));
        c.put("comprasRecientes", comprasRecientes(id));
        c.put("pedidosPorDespachar", pedidosPorDespachar(id, ahora));
        c.put("sinMovimiento90d", sinMovimiento(productos, vendidas90, ultimaVenta));
        c.putAll(margenes(productos, vendidas90));
        return c;
    }

    // ─── 2. Qué reponer y cuánto ─────────────────────────────────────────────

    /** Para cubrir 30 días al ritmo de los últimos 90 y volver al mínimo: ceil(vendidas90 / 3) + mínimo − stock. */
    static List<Reponer> reponer(List<ProductoBajo> bajos, Map<String, Long> vendidas90) {
        return bajos.stream().map(b -> {
            long v = vendidas90.getOrDefault(b.productoId(), 0L);
            long porRitmo = (long) Math.ceil(v * (double) DIAS_COBERTURA / DIAS_RITMO);
            return new Reponer(b.nombre(), b.codigo(), b.stock(), b.minimo(), v, Math.max(0, porRitmo + b.minimo() - b.stock()));
        }).toList();
    }

    // ─── 3. A quién comprarle ────────────────────────────────────────────────

    /** La compra más reciente de cada producto con cada proveedor: costo y fecha para comparar proveedores. */
    private List<Map<String, Object>> comprasRecientes(String tiendaId) {
        Map<String, String> nombreProveedor = proveedores.listarDeTienda(tiendaId).stream()
                .collect(Collectors.toMap(Proveedor::getId, p -> Objects.toString(p.getNombre(), "Sin nombre"), (a, b) -> a));
        List<Map<String, Object>> filas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        compras.listarDeTienda(tiendaId).stream()
                .filter(c -> c.getFecha() != null && c.getProductos() != null)
                .sorted(Comparator.comparing(Compra::getFecha).reversed())
                .forEach(c -> {
                    for (DetalleCompra d : c.getProductos()) {
                        if (d == null || filas.size() >= MAX_COMPRAS || !vistos.add(d.getProductoId() + "|" + c.getProveedorId())) continue;
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put("producto", d.getNombre());
                        f.put("proveedor", nombreProveedor.getOrDefault(c.getProveedorId(), "Proveedor eliminado"));
                        f.put("costoUnitario", d.getCosto());
                        f.put("cantidad", d.getCantidad());
                        f.put("fecha", c.getFecha().toLocalDate().toString());
                        filas.add(f);
                    }
                });
        return filas;
    }

    // ─── 4. Qué despachar primero ────────────────────────────────────────────

    /** Pedidos esperando a la tienda, el de plazo más cercano primero. Sin cliente, taller ni dirección. */
    private List<Map<String, Object>> pedidosPorDespachar(String tiendaId, LocalDateTime ahora) {
        return mongo.find(Query.query(Criteria.where("tiendaId").is(tiendaId)
                                .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION))
                        .with(Sort.by(Sort.Direction.ASC, "plazoDespachoHasta")).limit(MAX_PENDIENTES), PedidoTienda.class)
                .stream().map(p -> {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("pedido", p.getId() == null ? null : "#" + p.getId().substring(Math.max(0, p.getId().length() - 6)));
                    f.put("estado", p.getEstado());
                    f.put("entrega", p.getEntrega());
                    f.put("pagado", p.getFechaCreacion());
                    f.put("plazo", p.getPlazoDespachoHasta());
                    if (p.getPlazoDespachoHasta() != null) {
                        f.put("horasParaElPlazo", Duration.between(ahora, p.getPlazoDespachoHasta()).toHours());
                    }
                    f.put("items", p.getItems() == null ? List.of() : p.getItems().stream()
                            .map(i -> Objects.toString(i.getProductoNombre(), i.getDescripcion()) + " x" + Objects.requireNonNullElse(i.getCantidad(), 1))
                            .toList());
                    f.put("total", p.getTotal());
                    return f;
                }).toList();
    }

    // ─── 6. Productos sin movimiento ─────────────────────────────────────────

    /** Con stock y sin ventas en 90 días, el que más plata tiene quieta primero. */
    static List<SinMovimiento> sinMovimiento(List<Producto> productos, Map<String, Long> vendidas90, Map<String, LocalDate> ultimaVenta) {
        return productos.stream()
                .filter(p -> !"ENCARGO".equals(p.getTipo()) && p.getStockActual() != null && p.getStockActual() > 0
                        && vendidas90.getOrDefault(p.getId(), 0L) == 0)
                .map(p -> new SinMovimiento(p.getNombre(), p.getCodigo(), p.getStockActual(),
                        p.getPrecioCompra() == null ? null : p.getPrecioCompra() * p.getStockActual(), ultimaVenta.get(p.getId())))
                .sorted(Comparator.comparing(SinMovimiento::capitalInmovilizado, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_LISTA).toList();
    }

    // ─── 7. Márgenes ─────────────────────────────────────────────────────────

    /** Margen sobre el precio de venta con el precio de compra registrado en el producto. */
    static Map<String, Object> margenes(List<Producto> productos, Map<String, Long> vendidas90) {
        List<Margen> todos = productos.stream()
                .filter(p -> p.getPrecioCompra() != null && p.getPrecioCompra() > 0 && p.getPrecioVenta() != null && p.getPrecioVenta() > 0)
                .map(p -> {
                    double ganancia = p.getPrecioVenta() - p.getPrecioCompra();
                    long v = vendidas90.getOrDefault(p.getId(), 0L);
                    return new Margen(p.getNombre(), p.getPrecioCompra(), p.getPrecioVenta(),
                            Math.round(ganancia / p.getPrecioVenta() * 1000) / 10.0, ganancia, v, ganancia * v);
                }).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("masGanancia90d", todos.stream().filter(m -> m.ganancia90d() > 0)
                .sorted(Comparator.comparingDouble(Margen::ganancia90d).reversed()).limit(MAX_LISTA).toList());
        r.put("margenMasBajo", todos.stream().sorted(Comparator.comparingDouble(Margen::margenPct)).limit(MAX_LISTA).toList());
        r.put("productosSinPrecioDeCompra", productos.size() - todos.size());
        return r;
    }

    // ─── Semana (resumen por correo) ─────────────────────────────────────────

    public record VentasSemana(LocalDate desde, LocalDate hasta, double ventas, long pedidos,
                               double ventasAnterior, long pedidosAnterior, List<ProductoVendido> masVendidos) {}

    /** Ventas de la semana que empieza el lunes dado (hora de Colombia), contra la semana anterior. */
    public VentasSemana semana(String tiendaId, LocalDate lunes) {
        Document actual = totalEntre(tiendaId, lunes, lunes.plusDays(7));
        Document anterior = totalEntre(tiendaId, lunes.minusDays(7), lunes);
        List<ProductoVendido> top = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(vendidosEntre(tiendaId, lunes, lunes.plusDays(7))),
                Aggregation.unwind("items"),
                Aggregation.match(Criteria.where("items.productoId").ne(null)),
                Aggregation.group("items.productoId").first("items.productoNombre").as("nombre")
                        .sum("items.cantidad").as("unidades").sum("items.subtotal").as("ventas"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "unidades")),
                Aggregation.limit(3)), "pedidos_tienda", Document.class).getMappedResults().stream()
                .map(d -> new ProductoVendido(String.valueOf(d.get("_id")), d.getString("nombre"),
                        d.get("unidades") instanceof Number n ? n.longValue() : 0L,
                        d.get("ventas") instanceof Number n ? n.doubleValue() : 0.0))
                .toList();
        return new VentasSemana(lunes, lunes.plusDays(6), decimal(actual, "ventas"), (long) decimal(actual, "pedidos"),
                decimal(anterior, "ventas"), (long) decimal(anterior, "pedidos"), top);
    }

    private Document totalEntre(String tiendaId, LocalDate desde, LocalDate hasta) {
        return mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(vendidosEntre(tiendaId, desde, hasta)),
                Aggregation.group().count().as("pedidos").sum("total").as("ventas")), "pedidos_tienda", Document.class)
                .getUniqueMappedResult();
    }

    private static Criteria vendidosEntre(String tiendaId, LocalDate desde, LocalDate hasta) {
        return Criteria.where("tiendaId").is(tiendaId)
                .and("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO)
                .and("fechaCreacion").gte(local(desde)).lt(local(hasta));
    }

    /** Medianoche de Colombia en la hora del servidor, que es como se guardan las fechas LocalDateTime. */
    private static LocalDateTime local(LocalDate dia) {
        return dia.atStartOfDay(ZONA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static double decimal(Document d, String campo) {
        return d != null && d.get(campo) instanceof Number n ? n.doubleValue() : 0;
    }

    // ─── Ventas por producto ─────────────────────────────────────────────────

    private Map<String, Long> unidadesVendidasDesde(String tiendaId, LocalDateTime desde) {
        Map<String, Long> r = new HashMap<>();
        for (Document d : agregadoPorProducto(Criteria.where("fechaCreacion").gte(desde), tiendaId, false)) {
            r.put(String.valueOf(d.get("_id")), d.get("unidades") instanceof Number n ? n.longValue() : 0L);
        }
        return r;
    }

    private Map<String, LocalDate> ultimaVentaPorProducto(String tiendaId) {
        Map<String, LocalDate> r = new HashMap<>();
        for (Document d : agregadoPorProducto(new Criteria(), tiendaId, true)) {
            if (d.get("ultima") instanceof Date f) r.put(String.valueOf(d.get("_id")), f.toInstant().atZone(ZONA).toLocalDate());
        }
        return r;
    }

    private List<Document> agregadoPorProducto(Criteria extra, String tiendaId, boolean ultima) {
        Criteria vendidos = Criteria.where("tiendaId").is(tiendaId)
                .and("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO);
        var grupo = Aggregation.group("items.productoId").sum("items.cantidad").as("unidades");
        if (ultima) grupo = grupo.max("fechaCreacion").as("ultima");
        return mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(vendidos, extra)),
                Aggregation.unwind("items"),
                Aggregation.match(Criteria.where("items.productoId").ne(null)),
                grupo), "pedidos_tienda", Document.class).getMappedResults();
    }
}
