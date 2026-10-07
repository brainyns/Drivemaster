package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pagos del marketplace con Wompi: la tarifa de diagnóstico y la cotización.
 *
 * La confirmación llega desde PagoService, que ya verificó la transacción contra la
 * API de Wompi (estado, monto y moneda). Aquí:
 *  - se toma el pago de forma atómica (PENDIENTE → PROCESANDO): webhook y
 *    redirección pueden llegar a la vez y solo uno aplica los efectos;
 *  - el dinero recibido siempre queda PAGADO; si no se puede aplicar (la solicitud
 *    ya cambió, falta stock…) se registra un reembolso pendiente para el ADMIN.
 *
 * Cotización pagada: se descuenta el stock (con reversa si un ítem falla), se crea un
 * pedido por tienda y el reparto: cada tienda recibe sus repuestos con su IVA; el
 * taller, la mano de obra con su IVA menos el anticipo del diagnóstico (que ya
 * recibió en el reparto del diagnóstico).
 */
@Service
public class PagoMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(PagoMarketplaceService.class);

    public static final String DIAGNOSTICO = "DIAGNOSTICO";
    public static final String COTIZACION = "COTIZACION";
    private static final Set<String> CONCEPTOS = Set.of(DIAGNOSTICO, COTIZACION);
    private static final String SALIDA = "SALIDA";
    private static final String ENTRADA = "ENTRADA";

    private final PagoService pagoService;
    private final PagoRepository pagoRepository;
    private final OrdenServicioRepository ordenRepository;
    private final CotizacionRepository cotizacionRepository;
    private final PedidoTiendaRepository pedidoRepository;
    private final RepartoPagoRepository repartoRepository;
    private final ReembolsoRepository reembolsoRepository;
    private final TallerRepository tallerRepository;
    private final ProductoService productoService;
    private final MovimientoInventarioService movimientoService;
    private final MovimientoInventarioRepository movimientoRepository;
    private final UsuarioService usuarioService;
    private final TransicionOrdenService transiciones;
    private final CotizacionService cotizacionService;
    private final AvisosSolicitudServicio avisos;

    public PagoMarketplaceService(PagoService pagoService,
                                  PagoRepository pagoRepository,
                                  OrdenServicioRepository ordenRepository,
                                  CotizacionRepository cotizacionRepository,
                                  PedidoTiendaRepository pedidoRepository,
                                  RepartoPagoRepository repartoRepository,
                                  ReembolsoRepository reembolsoRepository,
                                  TallerRepository tallerRepository,
                                  ProductoService productoService,
                                  MovimientoInventarioService movimientoService,
                                  MovimientoInventarioRepository movimientoRepository,
                                  UsuarioService usuarioService,
                                  TransicionOrdenService transiciones,
                                  CotizacionService cotizacionService,
                                  AvisosSolicitudServicio avisos) {
        this.pagoService = pagoService;
        this.pagoRepository = pagoRepository;
        this.ordenRepository = ordenRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.pedidoRepository = pedidoRepository;
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.tallerRepository = tallerRepository;
        this.productoService = productoService;
        this.movimientoService = movimientoService;
        this.movimientoRepository = movimientoRepository;
        this.usuarioService = usuarioService;
        this.transiciones = transiciones;
        this.cotizacionService = cotizacionService;
        this.avisos = avisos;
    }

    public static boolean esMarketplace(String concepto) {
        return concepto != null && CONCEPTOS.contains(concepto);
    }

    /** Referencia de los movimientos de inventario de un pago (clave de idempotencia). */
    public static String referenciaInventario(PagoEntity pago) {
        return "PAGO-" + pago.getReferencia();
    }

    /**
     * Si el pago quedó aplicado. Wompi puede decir APPROVED y aun así el pago no
     * haberse aplicado (la solicitud cambió, faltó stock…): en ese caso hay un
     * reembolso pendiente y la pantalla debe decirlo.
     */
    public Map<String, Object> resumenAplicacion(PagoEntity pago) {
        boolean reembolso = pago.getId() != null && reembolsoRepository.existsByPagoId(pago.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("estadoPago", pago.getEstado());
        r.put("reembolsoPendiente", reembolso);
        r.put("aplicado", "PAGADO".equals(pago.getEstado()) && !reembolso);
        return r;
    }

    // ─── Links de pago (cliente con sesión) ──────────────────────────────────

    public Map<String, Object> iniciarPagoDiagnostico(String correoCliente, String ordenId) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        OrdenServicio orden = ordenDelCliente(cliente, ordenId);
        if (!EstadoOrden.ASIGNADA_A_TALLER.equals(orden.getEstado())
                || !Boolean.TRUE.equals(orden.getAceptadaPorTaller())
                || !EstadoOrden.PAGO_PENDIENTE.equals(orden.getPagoDiagnosticoEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta solicitud no tiene un diagnóstico pendiente de pago");
        }
        if (orden.getFechaLimitePagoDiagnostico() != null && orden.getFechaLimitePagoDiagnostico().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Venció el plazo para pagar el diagnóstico");
        }
        double tarifa = orden.getTarifaDiagnostico() != null ? orden.getTarifaDiagnostico() : 0;
        if (tarifa <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La tarifa de diagnóstico no está definida");
        }
        return pagoService.crearPagoMarketplace(DIAGNOSTICO, cliente.getId(), tarifa, orden.getId(), null);
    }

    /**
     * Antes de cobrar revisa que los repuestos en stock sigan disponibles: si alguno
     * ya no alcanza, no se cobra y la solicitud vuelve al taller para recotizar.
     */
    public Map<String, Object> iniciarPagoCotizacion(String correoCliente, String ordenId) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        OrdenServicio orden = ordenDelCliente(cliente, ordenId);
        if (!EstadoOrden.APROBADA.equals(orden.getEstado()) || orden.getCotizacionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta solicitud no tiene una cotización aprobada por pagar");
        }
        Cotizacion cotizacion = cotizacionRepository.findById(orden.getCotizacionId())
                .filter(c -> Cotizacion.APROBADA.equals(c.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La cotización ya no está aprobada"));
        if (cotizacion.getVigenciaHasta() != null && cotizacion.getVigenciaHasta().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cotización venció: el taller emitirá una nueva");
        }
        String faltante = primerFaltante(cotizacion);
        if (faltante != null) {
            String nota = "Sin stock suficiente de \"" + faltante + "\" al pagar";
            cotizacionService.devolverAprobadaADiagnosticada(orden, cotizacion, Cotizacion.ANULADA, nota);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "\"" + faltante + "\" ya no tiene stock suficiente. No se hizo ningún cobro: el taller actualizará la cotización.");
        }
        double monto = cotizacion.getTotalAPagar() != null ? cotizacion.getTotalAPagar() : 0;
        if (monto <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cotización no tiene saldo por pagar");
        }
        return pagoService.crearPagoMarketplace(COTIZACION, cliente.getId(), monto, orden.getId(), cotizacion.getId());
    }

    // ─── Confirmación (desde PagoService, ya verificada con Wompi) ──────────

    public void confirmar(PagoEntity pago, String transactionId) {
        if (pagoRepository.reclamar(pago.getId(), transactionId) == 0) {
            log.info("Pago {} ya procesado o en proceso (estado {}): se omite", pago.getReferencia(), pago.getEstado());
            return;
        }
        pago.setTransactionId(transactionId);
        pago.setFechaPago(LocalDateTime.now());
        pago.setEstado("PAGADO");
        try {
            if (DIAGNOSTICO.equals(pago.getConcepto())) {
                aplicarDiagnostico(pago);
            } else {
                aplicarCotizacion(pago);
            }
        } catch (Exception e) {
            log.error("Error aplicando el pago {} de la orden {}", pago.getReferencia(), pago.getOrdenServicioId(), e);
            registrarReembolso(pago, null, pago.getMonto(), "Error al aplicar el pago: revisar la solicitud antes de reembolsar");
            avisos.alertaAdmin("Pago sin aplicar", "El pago " + pago.getReferencia() + " de la solicitud "
                    + pago.getOrdenServicioId() + " falló al aplicarse: " + e.getMessage());
        } finally {
            pagoRepository.save(pago);
        }
    }

    private void aplicarDiagnostico(PagoEntity pago) {
        OrdenServicio orden = ordenRepository.findById(pago.getOrdenServicioId()).orElse(null);
        String nota = "Pago del diagnóstico confirmado (ref. " + pago.getReferencia() + ")";
        OrdenServicio enRevision;
        try {
            if (orden == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Solicitud no encontrada");
            enRevision = transiciones.transicionar(orden, EstadoOrden.EN_REVISION, Actor.SISTEMA, nota,
                    Criteria.where("pagoDiagnosticoEstado").is(EstadoOrden.PAGO_PENDIENTE).and("aceptadaPorTaller").is(true),
                    new Update().set("pagoDiagnosticoEstado", EstadoOrden.PAGO_PAGADO));
        } catch (ResponseStatusException e) {
            noAplicado(pago, orden, "La solicitud ya no esperaba el pago del diagnóstico"
                    + (orden != null ? " (estado " + orden.getEstado() + ")" : ""));
            return;
        }
        Taller taller = tallerRepository.findById(enRevision.getTallerId()).orElse(null);
        repartoRepository.save(reparto(pago, RepartoPago.TALLER, enRevision.getTallerId(),
                taller != null ? taller.getNombre() : null, pago.getMonto(), null));

        Map<String, Double> lineas = new LinkedHashMap<>();
        lineas.put("Tarifa de diagnóstico (IVA incluido)", pago.getMonto());
        avisos.comprobantePago(enRevision, pago, "la revisión y diagnóstico", lineas,
                "Tu cita de revisión quedó confirmada. El valor se descontará de la cotización si la apruebas.");
        avisos.diagnosticoPagado(enRevision);
    }

    private void aplicarCotizacion(PagoEntity pago) {
        OrdenServicio orden = ordenRepository.findById(pago.getOrdenServicioId()).orElse(null);
        Cotizacion cotizacion = pago.getCotizacionId() != null
                ? cotizacionRepository.findById(pago.getCotizacionId()).orElse(null) : null;
        if (orden == null || cotizacion == null || !EstadoOrden.APROBADA.equals(orden.getEstado())
                || !cotizacion.getId().equals(orden.getCotizacionId()) || !Cotizacion.APROBADA.equals(cotizacion.getEstado())) {
            noAplicado(pago, orden, "La cotización ya no esperaba este pago"
                    + (orden != null ? " (solicitud en estado " + orden.getEstado() + ")" : ""));
            return;
        }

        // 1. Stock: se descuenta lo que estaba en stock (con su SALIDA en el inventario);
        //    lo de encargo lo consigue la tienda
        String refInventario = referenciaInventario(pago);
        String motivoSalida = "Pedido marketplace · cotización v" + cotizacion.getVersion();
        List<CotizacionItem> descontados = new ArrayList<>();
        String faltante = null;
        for (CotizacionItem item : repuestos(cotizacion)) {
            if (!descuentaStock(item)) continue;
            // Idempotente: si este pago ya tiene su SALIDA de este producto, no se descuenta otra vez
            if (movimientoRepository.existsByProductoIdAndTipoAndReferencia(item.getProductoId(), SALIDA, refInventario)) {
                log.warn("Pago {}: la salida de {} ya estaba registrada, no se descuenta otra vez",
                        pago.getReferencia(), item.getProductoId());
                descontados.add(item);
                continue;
            }
            try {
                movimientoService.registrarMovimiento(item.getProductoId(), SALIDA, item.getCantidad(),
                        motivoSalida, refInventario, pago.getUsuarioId());
                descontados.add(item);
            } catch (StockInsuficienteException e) {
                faltante = item.getProductoNombre();
                break;
            } catch (Exception e) {
                log.error("Pago {}: no se pudo descontar el stock de {}", pago.getReferencia(), item.getProductoId(), e);
                faltante = item.getProductoNombre();
                break;
            }
        }
        Criteria mismaCotizacion = Criteria.where("cotizacionId").is(cotizacion.getId());
        String notaPago = "Pago de la cotización v" + cotizacion.getVersion() + " confirmado (ref. " + pago.getReferencia() + ")";

        if (faltante != null) {
            reponerStock(descontados, refInventario);
            String motivo = "Sin stock suficiente de \"" + faltante + "\" al confirmar el pago";
            try {
                OrdenServicio pagada = transiciones.transicionar(orden, EstadoOrden.PAGADA, Actor.SISTEMA, notaPago, mismaCotizacion, null);
                transiciones.transicionar(pagada, EstadoOrden.DIAGNOSTICADA, Actor.SISTEMA,
                        motivo + ": se reembolsa el pago y el taller debe emitir una nueva cotización", mismaCotizacion, null);
                cotizacionRepository.findById(cotizacion.getId()).ifPresent(c -> {
                    c.setEstado(Cotizacion.ANULADA);
                    c.setMotivoRespuesta(motivo);
                    cotizacionRepository.save(c);
                });
            } catch (ResponseStatusException e) {
                log.warn("Orden {}: no se pudo devolver a DIAGNOSTICADA tras falta de stock", orden.getId());
            }
            noAplicado(pago, orden, motivo);
            return;
        }

        // 2. Orden pagada (condicional: si el cliente canceló entretanto, se revierte el stock)
        OrdenServicio pagada;
        try {
            pagada = transiciones.transicionar(orden, EstadoOrden.PAGADA, Actor.SISTEMA, notaPago, mismaCotizacion,
                    new Update().set("pagoCotizacionId", pago.getId()));
        } catch (ResponseStatusException e) {
            reponerStock(descontados, refInventario);
            noAplicado(pago, orden, "La solicitud cambió mientras se confirmaba el pago");
            return;
        }

        // 3. Pedidos por tienda y reparto
        Taller taller = tallerRepository.findById(orden.getTallerId()).orElse(null);
        List<PedidoTienda> pedidos = crearPedidos(pago, pagada, cotizacion, taller);
        double paraTiendas = 0;
        for (PedidoTienda p : pedidos) {
            repartoRepository.save(reparto(pago, RepartoPago.TIENDA, p.getTiendaId(), p.getTiendaNombre(), p.getTotal(), p.getId()));
            paraTiendas += p.getTotal();
        }
        double paraTaller = Math.max(0, pago.getMonto() - paraTiendas);
        if (paraTaller > 0) {
            repartoRepository.save(reparto(pago, RepartoPago.TALLER, orden.getTallerId(),
                    taller != null ? taller.getNombre() : cotizacion.getTallerNombre(), paraTaller, null));
        }

        // 4. Siguiente estado: con repuestos se esperan los pedidos; sin ellos, listo para trabajar
        String siguiente = pedidos.isEmpty() ? EstadoOrden.REPUESTOS_RECIBIDOS : EstadoOrden.REPUESTOS_PEDIDOS;
        OrdenServicio actualizada = transiciones.transicionar(pagada, siguiente, Actor.SISTEMA,
                pedidos.isEmpty() ? "Cotización solo de mano de obra" : "Repuestos pedidos a " + pedidos.size() + " tienda(s)",
                null, null);

        avisos.comprobantePago(actualizada, pago, "la cotización v" + cotizacion.getVersion() + " de " + cotizacion.getTallerNombre(),
                lineasCotizacion(cotizacion),
                pedidos.isEmpty() ? "El taller ya puede empezar el trabajo."
                        : "Pedimos los repuestos a las tiendas; te avisaremos el avance.");
        avisos.cotizacionPagada(actualizada, pedidos);
    }

    private List<PedidoTienda> crearPedidos(PagoEntity pago, OrdenServicio orden, Cotizacion cotizacion, Taller taller) {
        Map<String, List<CotizacionItem>> porTienda = new LinkedHashMap<>();
        for (CotizacionItem item : repuestos(cotizacion)) {
            porTienda.computeIfAbsent(item.getTiendaId(), k -> new ArrayList<>()).add(item);
        }
        double ivaPct = cotizacion.getIvaPct() != null ? cotizacion.getIvaPct() : 0;
        List<PedidoTienda> pedidos = new ArrayList<>();
        for (List<CotizacionItem> items : porTienda.values()) {
            double subtotal = items.stream().mapToDouble(i -> i.getSubtotal() != null ? i.getSubtotal() : 0).sum();
            double iva = Math.round(subtotal * ivaPct / 100.0);
            Integer dias = items.stream()
                    .filter(i -> "ENCARGO".equals(i.getDisponibilidad()) && i.getDiasEntrega() != null)
                    .map(CotizacionItem::getDiasEntrega).max(Integer::compare).orElse(null);
            pedidos.add(pedidoRepository.save(PedidoTienda.builder()
                    .ordenId(orden.getId())
                    .cotizacionId(cotizacion.getId())
                    .pagoId(pago.getId())
                    .tiendaId(items.get(0).getTiendaId())
                    .tiendaNombre(items.get(0).getTiendaNombre())
                    .tallerId(orden.getTallerId())
                    .tallerNombre(taller != null ? taller.getNombre() : cotizacion.getTallerNombre())
                    .tallerDireccion(taller != null ? taller.getDireccion() : null)
                    .tallerCiudad(taller != null ? taller.getCiudad() : null)
                    .tallerTelefono(taller != null ? taller.getTelefono() : null)
                    .vehiculoDescripcion(orden.getVehiculoDescripcion())
                    .items(items)
                    .subtotal(subtotal)
                    .iva(iva)
                    .total(subtotal + iva)
                    .diasEntrega(dias)
                    .estado(PedidoTienda.PENDIENTE)
                    .fechaCreacion(LocalDateTime.now())
                    .build()));
        }
        return pedidos;
    }

    // ─── Reembolsos ──────────────────────────────────────────────────────────

    /** Registra un reembolso pendiente (lo hace el ADMIN a mano en Wompi). */
    public Reembolso registrarReembolso(PagoEntity pago, String pedidoTiendaId, double monto, String motivo) {
        return reembolsoRepository.save(Reembolso.builder()
                .pagoId(pago.getId())
                .ordenServicioId(pago.getOrdenServicioId())
                .pedidoTiendaId(pedidoTiendaId)
                .usuarioId(pago.getUsuarioId())
                .referenciaPago(pago.getReferencia())
                .transactionId(pago.getTransactionId())
                .monto(monto)
                .motivo(motivo.length() > 500 ? motivo.substring(0, 500) : motivo)
                .estado(Reembolso.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build());
    }

    private void noAplicado(PagoEntity pago, OrdenServicio orden, String motivo) {
        log.warn("Pago {} no aplicado: {}", pago.getReferencia(), motivo);
        registrarReembolso(pago, null, pago.getMonto(), motivo);
        avisos.pagoNoAplicado(orden, pago.getMonto(), motivo);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Primer repuesto en stock que ya no alcanza (o null si todos alcanzan). */
    private String primerFaltante(Cotizacion cotizacion) {
        for (CotizacionItem item : repuestos(cotizacion)) {
            if (!descuentaStock(item)) continue;
            try {
                Producto p = productoService.obtenerPorId(item.getProductoId());
                int stock = p.getStockActual() != null ? p.getStockActual() : 0;
                if (Boolean.FALSE.equals(p.getActivo()) || stock < item.getCantidad()) return item.getProductoNombre();
            } catch (RuntimeException e) {
                return item.getProductoNombre();
            }
        }
        return null;
    }

    private static boolean descuentaStock(CotizacionItem item) {
        return !"ENCARGO".equals(item.getDisponibilidad()) && item.getProductoId() != null
                && item.getCantidad() != null && item.getCantidad() > 0;
    }

    /** Devuelve el stock con una ENTRADA registrada (misma referencia que la salida). */
    private void reponerStock(List<CotizacionItem> descontados, String refInventario) {
        for (CotizacionItem item : descontados) {
            try {
                movimientoService.registrarMovimiento(item.getProductoId(), ENTRADA, item.getCantidad(),
                        "Reversa pedido marketplace (pago no aplicado)", refInventario, null);
            } catch (Exception e) {
                log.error("No se pudo reponer el stock de {} ({} u.)", item.getProductoId(), item.getCantidad(), e);
            }
        }
    }

    private static List<CotizacionItem> repuestos(Cotizacion c) {
        return c.getItems() == null ? List.of()
                : c.getItems().stream().filter(i -> CotizacionItem.REPUESTO.equals(i.getTipo())).toList();
    }

    private static Map<String, Double> lineasCotizacion(Cotizacion c) {
        Map<String, Double> lineas = new LinkedHashMap<>();
        if (c.getSubtotalRepuestos() != null && c.getSubtotalRepuestos() > 0) lineas.put("Repuestos", c.getSubtotalRepuestos());
        if (c.getSubtotalManoObra() != null && c.getSubtotalManoObra() > 0) lineas.put("Mano de obra", c.getSubtotalManoObra());
        lineas.put("IVA", c.getIva());
        if (c.getAnticipoDiagnostico() != null && c.getAnticipoDiagnostico() > 0) {
            lineas.put("Diagnóstico ya pagado", -c.getAnticipoDiagnostico());
        }
        return lineas;
    }

    private static RepartoPago reparto(PagoEntity pago, String tipo, String beneficiarioId, String nombre,
                                       double monto, String pedidoTiendaId) {
        return RepartoPago.builder()
                .pagoId(pago.getId())
                .ordenServicioId(pago.getOrdenServicioId())
                .cotizacionId(pago.getCotizacionId())
                .pedidoTiendaId(pedidoTiendaId)
                .concepto(pago.getConcepto())
                .beneficiarioTipo(tipo)
                .beneficiarioId(beneficiarioId)
                .beneficiarioNombre(nombre)
                .monto(monto)
                .estado(RepartoPago.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private OrdenServicio ordenDelCliente(Usuario cliente, String ordenId) {
        OrdenServicio orden = ordenRepository.findById(ordenId)
                .filter(o -> EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!cliente.getId().equals(orden.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no le pertenece");
        }
        return orden;
    }
}
