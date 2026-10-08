package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Despacho;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Informes;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Liquidacion;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.MesVenta;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoBajo;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoVendido;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.StockBajo;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.VentasPorMes;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Informes de la TIENDA. Todo se filtra por la tienda dueña (la que sale de la sesión): una tienda nunca ve datos de
 * otra. Los meses se cuentan en hora de Colombia (America/Bogota).
 */
@Service
public class InformesTiendaService {

    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int MESES_POR_DEFECTO = 6;
    static final int MESES_MAXIMO = 24;
    static final int TOP_PRODUCTOS = 10;
    static final int MAX_STOCK_BAJO = 20;
    static final int MINIMO_POR_DEFECTO = 5;

    private final MongoTemplate mongo;
    private final RepartoPagoRepository repartoRepository;

    public InformesTiendaService(MongoTemplate mongo, RepartoPagoRepository repartoRepository) {
        this.mongo = mongo;
        this.repartoRepository = repartoRepository;
    }

    public Informes informes(String tiendaId, int meses) {
        return informes(tiendaId, meses, YearMonth.now(ZONA), LocalDateTime.now());
    }

    Informes informes(String tiendaId, int meses, YearMonth hasta, LocalDateTime ahora) {
        if (tiendaId == null || tiendaId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta la tienda dueña");
        }
        int m = meses(meses);
        // Inicio del primer mes en hora de Colombia (para Mongo, que guarda instantes) y en la hora del servidor
        // (para las fechas que se leen como LocalDateTime)
        Date desde = Date.from(hasta.minusMonths(m - 1L).atDay(1).atStartOfDay(ZONA).toInstant());
        LocalDateTime desdeLocal = hasta.minusMonths(m - 1L).atDay(1).atStartOfDay(ZONA)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return new Informes(ahora, m, ventasPorMes(tiendaId, m, hasta, desde), productosMasVendidos(tiendaId, desde),
                stockBajo(tiendaId), pedidosPorEstado(tiendaId, desde), despacho(tiendaId, desdeLocal, ahora),
                liquidacion(tiendaId));
    }

    // ─── Ventas por mes ──────────────────────────────────────────────────────

    private VentasPorMes ventasPorMes(String tiendaId, int meses, YearMonth hasta, Date desde) {
        List<Document> filas = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(vendidos(tiendaId).and("fechaCreacion").gte(desde)),
                Aggregation.project("total")
                        .and(DateOperators.dateOf("fechaCreacion").withTimezone(DateOperators.Timezone.valueOf(ZONA.getId()))
                                .toString("%Y-%m")).as("mes"),
                Aggregation.group("mes").count().as("pedidos").sum("total").as("ventas")), "pedidos_tienda", Document.class)
                .getMappedResults();
        Map<String, MesVenta> porMes = new HashMap<>();
        for (Document d : filas) {
            String mes = String.valueOf(d.get("_id"));
            porMes.put(mes, new MesVenta(mes, numero(d.get("pedidos")), decimal(d.get("ventas"))));
        }
        List<MesVenta> lista = new ArrayList<>();
        long pedidos = 0;
        double ventas = 0;
        for (int i = meses - 1; i >= 0; i--) {
            String mes = hasta.minusMonths(i).toString();
            MesVenta v = porMes.getOrDefault(mes, new MesVenta(mes, 0, 0));
            lista.add(v);
            pedidos += v.pedidos();
            ventas += v.ventas();
        }
        return new VentasPorMes(lista, pedidos, ventas);
    }

    /** Pedidos de la tienda que no quedaron sin despachar ni sin entregar. */
    private static Criteria vendidos(String tiendaId) {
        return Criteria.where("tiendaId").is(tiendaId)
                .and("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO);
    }

    // ─── Productos más vendidos ──────────────────────────────────────────────

    private List<ProductoVendido> productosMasVendidos(String tiendaId, Date desde) {
        return mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(vendidos(tiendaId).and("fechaCreacion").gte(desde)),
                Aggregation.unwind("items"),
                Aggregation.match(Criteria.where("items.productoId").ne(null)),
                Aggregation.group("items.productoId").first("items.productoNombre").as("nombre")
                        .sum("items.cantidad").as("unidades").sum("items.subtotal").as("ventas"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "unidades")),
                Aggregation.limit(TOP_PRODUCTOS)), "pedidos_tienda", Document.class).getMappedResults().stream()
                .map(d -> new ProductoVendido(String.valueOf(d.get("_id")), d.getString("nombre"),
                        numero(d.get("unidades")), decimal(d.get("ventas"))))
                .toList();
    }

    // ─── Stock bajo ──────────────────────────────────────────────────────────

    private StockBajo stockBajo(String tiendaId) {
        List<ProductoBajo> bajos = mongo.find(Query.query(Criteria.where("tiendaId").is(tiendaId)
                        .and("activo").ne(false).and("tipo").ne("ENCARGO")), Producto.class).stream()
                .filter(p -> p.getStockActual() != null)
                .map(p -> new ProductoBajo(p.getId(), p.getCodigo(), p.getNombre(), p.getStockActual(),
                        p.getStockMinimo() != null ? p.getStockMinimo() : MINIMO_POR_DEFECTO))
                .filter(p -> p.stock() <= p.minimo())
                // Lo más urgente primero: al que más le falta respecto a su mínimo
                .sorted(Comparator.comparingInt((ProductoBajo p) -> p.stock() - p.minimo())
                        .thenComparing(ProductoBajo::nombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return new StockBajo(bajos.size(), bajos.stream().limit(MAX_STOCK_BAJO).toList());
    }

    // ─── Pedidos por estado ──────────────────────────────────────────────────

    private Map<String, Long> pedidosPorEstado(String tiendaId, Date desde) {
        Map<String, Long> r = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tiendaId").is(tiendaId).and("fechaCreacion").gte(desde)),
                Aggregation.group("estado").count().as("n")), "pedidos_tienda", Document.class).getMappedResults()) {
            r.put(String.valueOf(d.get("_id")), numero(d.get("n")));
        }
        return r;
    }

    // ─── Tiempo de despacho ──────────────────────────────────────────────────

    private Despacho despacho(String tiendaId, LocalDateTime desde, LocalDateTime ahora) {
        List<PedidoTienda> hechos = mongo.find(Query.query(Criteria.where("tiendaId").is(tiendaId)
                .and("fechaCreacion").gte(desde)
                .and("estado").in(PedidoTienda.DESPACHADO, PedidoTienda.ENTREGADO, PedidoTienda.RECIBIDO,
                        PedidoTienda.LISTO_PARA_RETIRAR, PedidoTienda.RETIRADO, PedidoTienda.NO_ENTREGADO)), PedidoTienda.class);
        long n = 0;
        long totalMinutos = 0;
        long aTiempo = 0;
        long conPlazo = 0;
        for (PedidoTienda p : hechos) {
            LocalDateTime listo = p.getFechaDespacho() != null ? p.getFechaDespacho() : p.getFechaListoRetiro();
            if (listo == null || p.getFechaCreacion() == null) continue;
            n++;
            totalMinutos += Math.max(0, Duration.between(p.getFechaCreacion(), listo).toMinutes());
            if (p.getPlazoDespachoHasta() != null) {
                conPlazo++;
                if (!listo.isAfter(p.getPlazoDespachoHasta())) aTiempo++;
            }
        }
        Criteria esperando = Criteria.where("tiendaId").is(tiendaId).and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION);
        long pendientes = mongo.count(Query.query(esperando), PedidoTienda.class);
        long atrasados = mongo.count(Query.query(Criteria.where("tiendaId").is(tiendaId)
                .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION)
                .and("plazoDespachoHasta").lt(ahora)), PedidoTienda.class);
        Double horas = n == 0 ? null : Math.round(totalMinutos / 60.0 / n * 10) / 10.0;
        Double pct = conPlazo == 0 ? null : Math.round(aTiempo * 1000.0 / conPlazo) / 10.0;
        return new Despacho(n, horas, pct, pendientes, atrasados);
    }

    // ─── Lo pendiente de liquidar ────────────────────────────────────────────

    private Liquidacion liquidacion(String tiendaId) {
        double espera = 0, listo = 0, pagado = 0, anulado = 0;
        long lEspera = 0, lListo = 0, lPagado = 0;
        for (Object[] fila : repartoRepository.resumenPorEstado(RepartoPago.TIENDA, tiendaId)) {
            String estado = String.valueOf(fila[0]);
            long lineas = fila[1] instanceof Number c ? c.longValue() : 0;
            double monto = fila[2] instanceof Number s ? s.doubleValue() : 0;
            switch (estado) {
                case RepartoPago.PENDIENTE, RepartoPago.PROCESANDO -> { espera += monto; lEspera += lineas; }
                case RepartoPago.POR_LIQUIDAR, RepartoPago.FALLIDO -> { listo += monto; lListo += lineas; }
                case RepartoPago.LIQUIDADO -> { pagado += monto; lPagado += lineas; }
                case RepartoPago.ANULADO -> anulado += monto;
                default -> { }
            }
        }
        return new Liquidacion(espera, lEspera, listo, lListo, pagado, lPagado, anulado);
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    static int meses(int pedido) {
        return pedido <= 0 ? MESES_POR_DEFECTO : Math.min(pedido, MESES_MAXIMO);
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static double decimal(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
