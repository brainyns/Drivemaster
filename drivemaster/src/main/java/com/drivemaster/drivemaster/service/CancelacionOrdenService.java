package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cancelación de una orden ya pagada: solo el SUPERADMIN, con motivo, y hasta antes de EN_INSTALACION. Después de
 * instalar no hay reembolso automático.
 *
 * Primero se mueve la orden: esa escritura condicional es la que decide. Si el taller inició la instalación un instante
 * antes, falla y no se toca nada; dos clics no cancelan dos veces. Desde ahí cada paso corre aunque otro falle, y lo que
 * falle se alerta: el cliente no puede quedar sin su reembolso porque, por ejemplo, un pedido no se pudo cancelar.
 *
 * Lo que una tienda ya entregó al taller (RECIBIDO) se queda en el taller: entra a su inventario como un lote, el cliente
 * recupera también esa parte y el taller la debe a DriveMaster (saldo a favor, se cobra a mano; la tienda cobra su venta).
 * Lo que va en camino (DESPACHADO) no se cancela: su parte no se reembolsa, su línea sigue su curso y el ADMIN lo resuelve
 * con la tienda y el taller. El diagnóstico tampoco se reembolsa: el taller ya lo hizo y su pago queda habilitado.
 *
 * El dinero: queda un reembolso pendiente y la línea del taller anulada. El ADMIN lo hace en Wompi, como los demás.
 */
@Service
public class CancelacionOrdenService {

    private static final Logger log = LoggerFactory.getLogger(CancelacionOrdenService.class);

    static final int MINIMO_MOTIVO = 10;
    public static final Set<String> CANCELABLES = Set.of(EstadoOrden.PAGADA, EstadoOrden.REPUESTOS_PEDIDOS, EstadoOrden.REPUESTOS_RECIBIDOS);
    private static final Set<String> SIN_DESPACHAR = Set.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION);

    private final OrdenServicioRepository ordenes;
    private final CotizacionRepository cotizaciones;
    private final PedidoTiendaRepository pedidos;
    private final PagoRepository pagos;
    private final RepartoPagoRepository repartos;
    private final ReembolsoRepository reembolsos;
    private final TransicionOrdenService transiciones;
    private final PagoMarketplaceService pagoMarketplace;
    private final StockTallerService stockTaller;
    private final MovimientoInventarioService movimientos;
    private final com.drivemaster.drivemaster.repository.MovimientoInventarioRepository movimientoRepository;
    private final LiquidacionService liquidacion;
    private final UsuarioService usuarios;
    private final AvisosSolicitudServicio avisos;
    private final MongoTemplate mongoTemplate;
    private final com.drivemaster.drivemaster.repository.TallerRepository talleres;
    private final InventarioTallerService inventarioTaller;

    public CancelacionOrdenService(OrdenServicioRepository ordenes, CotizacionRepository cotizaciones, PedidoTiendaRepository pedidos,
                                   PagoRepository pagos, RepartoPagoRepository repartos, ReembolsoRepository reembolsos,
                                   TransicionOrdenService transiciones, PagoMarketplaceService pagoMarketplace,
                                   StockTallerService stockTaller, MovimientoInventarioService movimientos,
                                   com.drivemaster.drivemaster.repository.MovimientoInventarioRepository movimientoRepository,
                                   LiquidacionService liquidacion, UsuarioService usuarios, AvisosSolicitudServicio avisos,
                                   MongoTemplate mongoTemplate, com.drivemaster.drivemaster.repository.TallerRepository talleres,
                                   InventarioTallerService inventarioTaller) {
        this.ordenes = ordenes;
        this.cotizaciones = cotizaciones;
        this.pedidos = pedidos;
        this.pagos = pagos;
        this.repartos = repartos;
        this.reembolsos = reembolsos;
        this.transiciones = transiciones;
        this.pagoMarketplace = pagoMarketplace;
        this.stockTaller = stockTaller;
        this.movimientos = movimientos;
        this.movimientoRepository = movimientoRepository;
        this.liquidacion = liquidacion;
        this.usuarios = usuarios;
        this.avisos = avisos;
        this.mongoTemplate = mongoTemplate;
        this.talleres = talleres;
        this.inventarioTaller = inventarioTaller;
    }

    /** Lo que pasó: cuánto se reembolsa, cuántos pedidos se cancelaron y cuáles ya habían salido de la tienda. */
    public record Resultado(double reembolso, int pedidosCancelados, List<String> pedidosYaDespachados, int piezasDevueltas,
                            double deudaTaller) {
    }

    /** Si esta orden se puede cancelar: pagada de verdad y antes de la instalación. */
    public static boolean sePuedeCancelar(OrdenServicio o) {
        return o != null && EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()) && CANCELABLES.contains(o.getEstado())
                && o.getPagoCotizacionId() != null;
    }

    public Resultado cancelar(String correoAdmin, String ordenId, String motivo) {
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < MINIMO_MOTIVO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explica en al menos " + MINIMO_MOTIVO + " caracteres por qué se cancela");
        }
        OrdenServicio orden = ordenes.findById(ordenId).filter(o -> EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!sePuedeCancelar(orden)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CANCELABLES.contains(orden.getEstado())
                    ? "Esta solicitud no tiene un pago de cotización que reembolsar"
                    : "Solo se cancela una solicitud pagada antes de iniciar la instalación (estado: " + EstadoOrden.nombre(orden.getEstado()) + ")");
        }
        PagoEntity pago = pagos.findById(orden.getPagoCotizacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No se encontró el pago de esta solicitud"));
        Usuario admin = usuarios.obtenerPorCorreo(correoAdmin);

        Taller taller = orden.getTallerId() != null ? talleres.findById(orden.getTallerId()).orElse(null) : null;
        String texto = "Cancelada por DriveMaster: " + razon;
        OrdenServicio cancelada = transiciones.transicionar(orden, EstadoOrden.CANCELADA,
                new Actor(EstadoOrden.ADMIN, admin.getId(), admin.getNombre()), texto, null,
                new Update().set("motivoCierre", texto));

        List<String> yaDespachados = new ArrayList<>();
        List<PedidoTienda> enElTaller = new ArrayList<>();
        double[] retenido = {0};
        double[] deudaTaller = {0};
        int[] cancelados = {0};
        int[] piezas = {0};
        double[] reembolso = {0};
        String refInventario = PagoMarketplaceService.referenciaInventario(pago);

        paso(ordenId, "cancelar los pedidos a las tiendas", () -> {
            for (PedidoTienda p : pedidos.findByPagoId(pago.getId())) {
                if (PedidoTienda.NO_DESPACHADO.equals(p.getEstado())) continue; // ya se reembolsó por su cuenta
                if (cancelarPedido(p, texto, ordenId)) {
                    cancelados[0]++;
                } else if (PedidoTienda.RECIBIDO.equals(p.getEstado())) {
                    // Ya está en el taller: la tienda cobra su venta, la pieza se queda en el taller y él la debe
                    enElTaller.add(p);
                    deudaTaller[0] += valor(p.getTotal());
                } else {
                    // Va en camino: ni está en la tienda ni en el taller. Lo resuelve el ADMIN
                    yaDespachados.add(p.getTiendaNombre());
                    retenido[0] += valor(p.getTotal());
                }
            }
        });
        paso(ordenId, "anular la línea del taller", () -> {
            for (RepartoPago r : repartos.findByPagoId(pago.getId())) {
                if (!RepartoPago.TALLER.equals(r.getBeneficiarioTipo())) continue;
                if (repartos.anularPendiente(r.getId(), "Orden cancelada: " + recortar(razon)) == 0 && !RepartoPago.ANULADO.equals(r.getEstado())) {
                    throw new IllegalStateException("La línea " + r.getId() + " del taller está " + r.getEstado());
                }
            }
        });
        paso(ordenId, "registrar el reembolso", () -> {
            double yaDevuelto = reembolsos.findByPagoId(pago.getId()).stream()
                    .mapToDouble(Reembolso::getMonto).sum();
            reembolso[0] = Math.max(0, valor(pago.getMonto()) - yaDevuelto - retenido[0]);
            if (reembolso[0] > 0) {
                pagoMarketplace.registrarReembolsoDeCancelacion(pago, reembolso[0], texto, deudaTaller[0], orden.getTallerId(),
                        taller != null ? taller.getNombre() : orden.getTallerNombre());
            }
        });
        paso(ordenId, "devolver las piezas al inventario del taller",
                () -> piezas[0] = stockTaller.revertir(refInventario, texto, admin.getId(), MovimientoPiezaTaller.CANCELACION));
        paso(ordenId, "pasar al inventario del taller los repuestos que ya le habían llegado", () -> {
            for (PedidoTienda p : enElTaller) {
                if (taller == null) throw new IllegalStateException("No se encontró el taller " + orden.getTallerId());
                inventarioTaller.entrarPedido(taller, p, admin.getId(), "Repuesto de la orden #" + ReferenciasMovimiento.corta(ordenId)
                        + " cancelada: ya estaba en el taller y queda en tu inventario", MovimientoPiezaTaller.CANCELACION);
            }
        });
        paso(ordenId, "habilitar el pago del diagnóstico", () -> liquidacion.diagnosticoPrestado(ordenId, "ORDEN_CANCELADA_POR_ADMIN"));
        paso(ordenId, "anular la cotización", () -> cotizaciones.findById(orden.getCotizacionId()).ifPresent(c -> {
            c.setEstado(Cotizacion.ANULADA);
            c.setMotivoRespuesta(texto);
            cotizaciones.save(c);
        }));
        paso(ordenId, "avisar al cliente y al taller", () -> avisos.ordenPagadaCancelada(cancelada, reembolso[0], razon, deudaTaller[0]));
        if (!yaDespachados.isEmpty()) {
            avisos.alertaAdmin("Cancelación con pedidos despachados", "La solicitud " + ordenId + " se canceló, pero "
                    + String.join(", ", yaDespachados) + " ya había despachado: esa parte no se reembolsó. Resuélvelo con la tienda y el taller.");
        }
        if (!enElTaller.isEmpty()) {
            avisos.alertaAdmin("Saldo a favor por cobrar", "La solicitud " + ordenId + " se canceló con repuestos de tienda que ya estaban en "
                    + (taller != null ? taller.getNombre() : "el taller") + ": quedaron en su inventario y debe " + Math.round(deudaTaller[0])
                    + " COP a DriveMaster. Cóbralo y márcalo en Reportes.");
        }
        return new Resultado(reembolso[0], cancelados[0], yaDespachados, piezas[0], deudaTaller[0]);
    }

    /**
     * Cancela el pedido si la tienda aún no lo despacha, anula su línea y le devuelve sus unidades. false si ya salió,
     * incluso si despachó justo ahora: la escritura condicional lo decide.
     */
    private boolean cancelarPedido(PedidoTienda p, String texto, String ordenId) {
        if (!SIN_DESPACHAR.contains(p.getEstado())) return false;
        PedidoTienda cancelado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(p.getId()).and("estado").in(SIN_DESPACHAR)),
                new Update().set("estado", PedidoTienda.NO_DESPACHADO).set("fechaNoDespacho", LocalDateTime.now())
                        .set("tipoMotivoNoDespacho", PedidoTienda.MOTIVO_ORDEN_CANCELADA)
                        .set("motivoNoDespacho", texto),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (cancelado == null) return false;
        for (RepartoPago r : repartos.findByPedidoTiendaId(p.getId())) {
            if (RepartoPago.TIENDA.equals(r.getBeneficiarioTipo())) repartos.anularPendiente(r.getId(), recortar(texto));
        }
        devolverStock(p, texto, ordenId);
        avisos.pedidoCanceladoPorOrden(cancelado);
        return true;
    }


    /**
     * Las unidades en stock del pedido vuelven a la tienda: una ENTRADA "Devolución por cancelación" con la referencia de la
     * orden y el motivo. La referencia es la misma si se repite: lo que ya volvió no vuelve dos veces. Los encargos nunca se
     * descontaron. Si un producto falla, se alerta y siguen los demás.
     */
    void devolverStock(PedidoTienda p, String texto, String ordenId) {
        String referencia = "CANCELACION-" + ordenId + "-" + p.getId();
        for (CotizacionItem item : p.getItems() != null ? p.getItems() : List.<CotizacionItem>of()) {
            if ("ENCARGO".equals(item.getDisponibilidad()) || item.getProductoId() == null
                    || item.getCantidad() == null || item.getCantidad() <= 0) continue;
            if (movimientoRepository.existsByProductoIdAndTipoAndReferencia(item.getProductoId(), "ENTRADA", referencia)) continue;
            try {
                movimientos.registrarMovimiento(item.getProductoId(), "ENTRADA", item.getCantidad(),
                        recortar("Devolución por cancelación de la orden " + ordenId + ". " + texto), referencia, null);
            } catch (RuntimeException e) {
                log.error("Orden {} cancelada: no volvió al stock de {} el producto {}", ordenId, p.getTiendaNombre(), item.getProductoId(), e);
                avisos.alertaAdmin("Stock sin devolver", "La orden " + ordenId + " se canceló, pero " + item.getCantidad() + " u. de \""
                        + item.getProductoNombre() + "\" no volvieron al stock de " + p.getTiendaNombre() + ": corrígelo a mano.");
            }
        }
    }
    private void paso(String ordenId, String nombre, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            log.error("Solicitud {} cancelada, pero no se pudo {}", ordenId, nombre, e);
            avisos.alertaAdmin("Cancelación incompleta", "La solicitud " + ordenId + " quedó cancelada, pero no se pudo " + nombre
                    + ": hazlo a mano.");
        }
    }

    private static String recortar(String t) {
        return t.length() > 400 ? t.substring(0, 400) : t;
    }

    private static double valor(Double v) {
        return v != null ? v : 0;
    }
}
