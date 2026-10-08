package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.Alertas;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.Clientes;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.Dinero;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.Negocios;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.ProductoVendido;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.Productos;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.TiendaVendedora;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cifras del panel de la plataforma. Es de solo lectura y solo entrega conteos y totales: no expone datos
 * personales de clientes ni de las cuentas de los negocios.
 */
@Service
public class PlataformaDashboardService {

    private static final int TOP = 5;

    private final MongoTemplate mongo;
    private final PagoRepository pagoRepository;
    private final RepartoPagoRepository repartoRepository;
    private final ReembolsoRepository reembolsoRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final ParametrosMarketplace parametros;

    public PlataformaDashboardService(MongoTemplate mongo, PagoRepository pagoRepository,
                                      RepartoPagoRepository repartoRepository, ReembolsoRepository reembolsoRepository,
                                      SuscripcionRepository suscripcionRepository, ParametrosMarketplace parametros) {
        this.mongo = mongo;
        this.pagoRepository = pagoRepository;
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.parametros = parametros;
    }

    public DashboardPlataformaDTO dashboard() {
        return dashboard(LocalDateTime.now());
    }

    /** Visible para pruebas: el panel a la hora dada. */
    public DashboardPlataformaDTO dashboard(LocalDateTime ahora) {
        Negocios tiendas = negocios(Tienda.class, Criteria.where("plataforma").ne(true));
        Negocios talleres = negocios(Taller.class, new Criteria());

        Instant hace7 = ahora.minusDays(7).atZone(ZoneId.systemDefault()).toInstant();
        Instant hace30 = ahora.minusDays(30).atZone(ZoneId.systemDefault()).toInstant();
        Clientes clientes = new Clientes(
                mongo.count(Query.query(Criteria.where("rol").is("CLIENTE")), Usuario.class),
                mongo.count(Query.query(Criteria.where("rol").is("CLIENTE").and("fechaCreacion").gte(hace7)), Usuario.class),
                mongo.count(Query.query(Criteria.where("rol").is("CLIENTE").and("fechaCreacion").gte(hace30)), Usuario.class));

        Productos productos = new Productos(
                mongo.count(new Query(), Producto.class),
                mongo.count(Query.query(Criteria.where("activo").ne(false)), Producto.class),
                productosNuevos(ahora.minusDays(7)),
                productosNuevos(ahora.minusDays(30)));

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (Document d : agrupar("ordenes_servicio",
                Aggregation.match(Criteria.where("flujo").is(EstadoOrden.FLUJO_MARKETPLACE)),
                Aggregation.group("estado").count().as("n"))) {
            porEstado.put(String.valueOf(d.get("_id")), numero(d.get("n")));
        }

        Map<String, Long> planes = new LinkedHashMap<>();
        for (Suscripcion s : suscripcionRepository.findByEstado(Suscripcion.ACTIVA)) {
            planes.merge(s.getPlanCodigo(), 1L, Long::sum);
        }

        Dinero dinero = new Dinero(pagoRepository.sumaMovidaMarketplace(), repartoRepository.sumaComisionRegistrada(),
                parametros.comisionPct(), repartoRepository.sumaPorLiquidar());

        Alertas alertas = new Alertas(
                mongo.count(Query.query(Criteria.where("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION)
                        .and("plazoDespachoHasta").lt(ahora)), PedidoTienda.class),
                mongo.count(Query.query(Criteria.where("problemaReportado").is(true).and("resolucionProblema").is(null)),
                        PedidoTienda.class),
                repartoRepository.countByEstado(RepartoPago.POR_LIQUIDAR),
                repartoRepository.countByEstado(RepartoPago.FALLIDO),
                reembolsoRepository.countByEstado(Reembolso.PENDIENTE),
                reembolsoRepository.sumaPendiente(),
                tiendas.pendientes() + talleres.pendientes());

        return new DashboardPlataformaDTO(ahora, tiendas, talleres, clientes, productos, productosMasVendidos(),
                tiendasMasVendedoras(), porEstado, dinero, planes, alertas);
    }

    // ─── Piezas ──────────────────────────────────────────────────────────────

    /** Las cuentas anteriores al marketplace no tienen estado y se consideran aprobadas. */
    private Negocios negocios(Class<?> tipo, Criteria base) {
        long total = mongo.count(Query.query(base), tipo);
        long pendientes = mongo.count(Query.query(new Criteria().andOperator(base,
                Criteria.where("estadoAprobacion").is(EstadoAprobacion.PENDIENTE))), tipo);
        long rechazadas = mongo.count(Query.query(new Criteria().andOperator(base,
                Criteria.where("estadoAprobacion").is(EstadoAprobacion.RECHAZADO))), tipo);
        return new Negocios(total, total - pendientes - rechazadas, pendientes, rechazadas);
    }

    /**
     * Productos ingresados desde la fecha. Se cuenta por el momento en que Mongo creó el documento
     * (el _id), así incluye también los productos anteriores al campo fechaCreacion.
     */
    private long productosNuevos(LocalDateTime desde) {
        Date fecha = Date.from(desde.atZone(ZoneId.systemDefault()).toInstant());
        return mongo.count(Query.query(Criteria.where("_id").gte(new ObjectId(fecha))), Producto.class);
    }

    private List<ProductoVendido> productosMasVendidos() {
        return agrupar("pedidos_tienda",
                Aggregation.match(Criteria.where("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO)),
                Aggregation.unwind("items"),
                Aggregation.match(Criteria.where("items.productoId").ne(null)),
                Aggregation.group("items.productoId").first("items.productoNombre").as("nombre")
                        .first("tiendaNombre").as("tienda")
                        .sum("items.cantidad").as("unidades").sum("items.subtotal").as("ventas"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "unidades")),
                Aggregation.limit(TOP)).stream()
                .map(d -> new ProductoVendido(String.valueOf(d.get("_id")), texto(d.get("nombre")), texto(d.get("tienda")),
                        numero(d.get("unidades")), decimal(d.get("ventas"))))
                .toList();
    }

    private List<TiendaVendedora> tiendasMasVendedoras() {
        return agrupar("pedidos_tienda",
                Aggregation.match(Criteria.where("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO)),
                Aggregation.group("tiendaId").first("tiendaNombre").as("nombre").count().as("pedidos")
                        .sum("total").as("ventas"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "ventas")),
                Aggregation.limit(TOP)).stream()
                .map(d -> new TiendaVendedora(String.valueOf(d.get("_id")), texto(d.get("nombre")),
                        numero(d.get("pedidos")), decimal(d.get("ventas"))))
                .toList();
    }

    private List<Document> agrupar(String coleccion, AggregationOperation... pasos) {
        return mongo.aggregate(Aggregation.newAggregation(pasos), coleccion, Document.class).getMappedResults();
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static double decimal(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
