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
import com.drivemaster.drivemaster.util.CalculadoraCotizacion;
import com.drivemaster.drivemaster.util.Dinero;
import com.drivemaster.drivemaster.util.PlazoHabil;
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

import java.math.BigDecimal;
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
 * pedido por tienda y el reparto: cada tienda recibe sus repuestos con su IVA; las piezas
 * del inventario del taller salen de sus lotes, sin pedido, y su valor va en la línea del taller; el
 * taller, la mano de obra con su IVA menos el anticipo del diagnóstico (que ya
 * recibió en el reparto del diagnóstico). Si la comisión se retiene, cada uno la paga
 * sobre lo suyo sin IVA (ver ComisionPlataforma): la tienda sobre sus repuestos y el
 * taller sobre su mano de obra menos la base del diagnóstico.
 */
@Service
public class PagoMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(PagoMarketplaceService.class);

    public static final String DIAGNOSTICO = "DIAGNOSTICO";
    public static final String COTIZACION = "COTIZACION";
    // Compra directa del catálogo (sin taller): la aplica CompraCatalogoService
    public static final String CATALOGO = "CATALOGO";
    private static final Set<String> CONCEPTOS = Set.of(DIAGNOSTICO, COTIZACION, CATALOGO);
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
    private final ParametrosMarketplace parametros;
    // Inyección por setter y perezosa: CompraCatalogoService depende de PagoService, que depende de esta clase
    private CompraCatalogoService catalogo;

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
                                  AvisosSolicitudServicio avisos,
                                  ParametrosMarketplace parametros) {
        this.parametros = parametros;
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

    private MongoTemplate mongoTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private com.drivemaster.drivemaster.repository.TiendaRepository tiendaRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTiendaRepository(com.drivemaster.drivemaster.repository.TiendaRepository tiendaRepository) {
        this.tiendaRepository = tiendaRepository;
    }

    /**
     * Nombre de la primera tienda de la cotización que ya no puede vender (no aprobada, en pausa o archivada), o null.
     * Los pedidos ya pagados no pasan por aquí: siguen su curso.
     */
    String primeraTiendaInactiva(Cotizacion cotizacion) {
        if (tiendaRepository == null || cotizacion.getItems() == null) return null;
        for (var item : cotizacion.getItems()) {
            if (item.getTiendaId() == null) continue;
            var tienda = tiendaRepository.findById(item.getTiendaId()).orElse(null);
            if (tienda == null || !com.drivemaster.drivemaster.model.EstadoAprobacion.estaAprobado(tienda.getEstadoAprobacion())
                    || !Boolean.TRUE.equals(tienda.getActivo())) {
                return tienda != null ? tienda.getNombre() : (item.getTiendaNombre() != null ? item.getTiendaNombre() : "una tienda");
            }
        }
        return null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setCatalogo(@org.springframework.context.annotation.Lazy CompraCatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    private StockTallerService stockTaller;

    @org.springframework.beans.factory.annotation.Autowired
    public void setStockTaller(StockTallerService stockTaller) {
        this.stockTaller = stockTaller;
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
        if (pago.getCompraCatalogoId() != null) r.put("compraId", pago.getCompraCatalogoId());
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
        // Una tienda de la cotización dejó de vender (en pausa o archivada): no se cobra y el taller vuelve a cotizar
        String tiendaInactiva = primeraTiendaInactiva(cotizacion);
        if (tiendaInactiva != null) {
            if (cotizacionService.devolverAprobadaADiagnosticada(orden, cotizacion, Cotizacion.ANULADA,
                    "Repuestos de " + tiendaInactiva + ", que ya no está disponible")) {
                avisos.cotizacionTiendaNoDisponible(cotizacion, tiendaInactiva);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta cotización tiene repuestos de una tienda que ya no está disponible. El taller te enviará una nueva.");
        }
        String faltante = primerFaltante(cotizacion);
        if (faltante != null) {
            String nota = "Sin stock suficiente de \"" + faltante + "\" al pagar";
            boolean devuelta = cotizacionService.devolverAprobadaADiagnosticada(orden, cotizacion, Cotizacion.ANULADA, nota);
            if (devuelta && repuestos(cotizacion).stream().anyMatch(i -> i.esDelTaller() && faltante.equals(i.getProductoNombre()))) {
                avisos.piezaTallerSinStock(cotizacion, faltante);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "\"" + faltante + "\" ya no tiene stock suficiente. No se hizo ningún cobro: el taller actualizará la cotización.");
        }
        // Aprobada antes de pasar sola al taller: no hay nada que cobrar y no se puede quedar aquí
        if (CotizacionService.sinSaldo(cotizacion)) {
            cotizacionService.pasarSinPago(orden, cotizacion);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cotización no tiene saldo por pagar: el trabajo ya pasó al taller");
        }
        double monto = cotizacion.getTotalAPagar() != null ? cotizacion.getTotalAPagar() : 0;
        if (monto <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cotización no tiene saldo por pagar");
        }
        // Si ya hay un link pendiente de esta cotización por el mismo monto, se devuelve ese: un pago por intento
        long centavos = Math.round(monto * 100);
        PagoEntity pendiente = pagoRepository
                .findFirstByOrdenServicioIdAndCotizacionIdAndUsuarioIdAndConceptoAndEstadoOrderByIdDesc(
                        orden.getId(), cotizacion.getId(), cliente.getId(), COTIZACION, "PENDIENTE")
                .filter(p -> p.getMonto() != null && Math.round(p.getMonto() * 100) == centavos)
                .orElse(null);
        if (pendiente != null) {
            return pagoService.reusarPagoMarketplace(pendiente);
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
            } else if (CATALOGO.equals(pago.getConcepto())) {
                catalogo.aplicar(pago);
            } else {
                aplicarCotizacion(pago);
            }
        } catch (Exception e) {
            log.error("Error aplicando el pago {} de la orden {}", pago.getReferencia(), pago.getOrdenServicioId(), e);
            anularLineasDelPago(pago);
            registrarReembolso(pago, null, pago.getMonto(), "Error al aplicar el pago: revisar la solicitud antes de reembolsar");
            avisos.alertaAdmin("Pago sin aplicar", "El pago " + pago.getReferencia() + " de la solicitud "
                    + pago.getOrdenServicioId() + " falló al aplicarse: " + e.getMessage());
        } finally {
            pagoRepository.save(pago);
        }
    }

    /**
     * El pago falló a mitad de aplicarse y se reembolsa todo: las líneas que alcanzaron a crearse no se deben pagar. Nunca
     * lanza: un fallo aquí no puede impedir que quede registrado el reembolso.
     */
    private void anularLineasDelPago(PagoEntity pago) {
        try {
            for (RepartoPago r : repartoRepository.findByPagoId(pago.getId())) {
                if (RepartoPago.PENDIENTE.equals(r.getEstado())) {
                    repartoRepository.anularPendiente(r.getId(), "El pago no se pudo aplicar: se reembolsa al cliente");
                }
            }
        } catch (RuntimeException e) {
            log.error("No se pudieron anular las líneas del pago {} que no se aplicó", pago.getReferencia(), e);
            avisos.alertaAdmin("Líneas sin anular", "El pago " + pago.getReferencia() + " no se aplicó y se reembolsa, pero sus "
                    + "líneas de reparto no se pudieron anular: no las liquides y anúlalas antes de cerrar el reembolso.");
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
        // Desde aquí, un fallo reembolsa todo el pago (ver confirmar): la orden no puede seguir como si estuviera pagada
        try {
            Taller taller = tallerRepository.findById(enRevision.getTallerId()).orElse(null);
            BigDecimal base = ComisionPlataforma.baseDiagnostico(enRevision, pago.getMonto(), parametros.ivaPct());
            double pct = parametros.comisionPct();
            boolean retener = parametros.comisionRetenida();
            RepartoPago lineaTaller = reparto(pago, RepartoPago.TALLER, enRevision.getTallerId(),
                    taller != null ? taller.getNombre() : null, pago.getMonto(), null);
            ComisionPlataforma.aplicar(lineaTaller, base, pct, retener);
            repartoRepository.save(lineaTaller);
            ComisionPlataforma.registrar(repartoRepository, pago, base, pct, retener, List.of(lineaTaller));
        } catch (RuntimeException e) {
            devolverDiagnosticoTrasFallo(pago, enRevision);
            throw e;
        }

        Map<String, Double> lineas = new LinkedHashMap<>();
        // Un taller no responsable de IVA cobra el diagnóstico sin IVA (las solicitudes anteriores no traen el dato)
        boolean sinIva = enRevision.getIvaDiagnosticoPct() != null && enRevision.getIvaDiagnosticoPct() == 0;
        lineas.put(sinIva ? "Tarifa de diagnóstico (sin IVA: el taller no es responsable de IVA)"
                : "Tarifa de diagnóstico (IVA incluido)", pago.getMonto());
        // El pago ya quedó aplicado: un aviso que falla no puede convertirlo en un reembolso
        avisarPagoAplicado(pago, () -> {
            avisos.comprobantePago(enRevision, pago, "la revisión y diagnóstico", lineas,
                    "Tu cita de revisión quedó confirmada. El valor se descontará de la cotización si la apruebas.");
            avisos.diagnosticoPagado(enRevision);
        });
    }

    /**
     * Mismo criterio que la cotización: el pago se reembolsa y la orden vuelve a donde estaba antes de pagar, esperando el
     * pago con un plazo nuevo (el cliente pagó a tiempo; como al proponer la cita, nunca después de ella). Si no paga, el
     * vencimiento normal la cancela. Nunca lanza: si no se puede, se alerta al ADMIN.
     */
    private void devolverDiagnosticoTrasFallo(PagoEntity pago, OrdenServicio enRevision) {
        String motivo = "El pago del diagnóstico no se pudo aplicar: se reembolsa y se puede pagar de nuevo";
        try {
            LocalDateTime limite = LocalDateTime.now().plusMinutes(parametros.timeoutPagoDiagnosticoMin());
            if (enRevision.getFechaProgramada() != null && limite.isAfter(enRevision.getFechaProgramada())) {
                limite = enRevision.getFechaProgramada();
            }
            transiciones.transicionar(enRevision, EstadoOrden.ASIGNADA_A_TALLER, Actor.SISTEMA, motivo,
                    Criteria.where("pagoDiagnosticoEstado").is(EstadoOrden.PAGO_PAGADO),
                    new Update().set("pagoDiagnosticoEstado", EstadoOrden.PAGO_PENDIENTE).set("fechaLimitePagoDiagnostico", limite));
            avisos.pagoNoAplicado(enRevision, pago.getMonto(), motivo);
        } catch (RuntimeException e) {
            log.error("No se pudo devolver la orden {} tras fallar el pago {}", enRevision.getId(), pago.getReferencia(), e);
            avisos.alertaAdmin("Orden sin corregir", "El pago " + pago.getReferencia() + " no se aplicó y se reembolsa, pero la solicitud "
                    + enRevision.getId() + " sigue EN_REVISION: corrígela a mano.");
        }
    }

    /** Lo último de un pago aplicado. Si un aviso falla, el pago sigue aplicado: queda el log y la alerta al ADMIN. Nunca lanza. */
    private void avisarPagoAplicado(PagoEntity pago, Runnable aviso) {
        try {
            aviso.run();
        } catch (RuntimeException e) {
            log.error("Pago {} aplicado, pero no se pudieron enviar sus avisos", pago.getReferencia(), e);
            try {
                avisos.alertaAdmin("Avisos sin enviar", "El pago " + pago.getReferencia()
                        + " quedó aplicado, pero no se pudieron enviar sus avisos: confírmale al cliente y a los negocios.");
            } catch (RuntimeException sinAlerta) {
                log.error("Tampoco se pudo alertar al ADMIN por los avisos del pago {}", pago.getReferencia(), sinAlerta);
            }
        }
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
        //    lo de encargo lo consigue la tienda y lo del taller sale de sus lotes
        String refInventario = referenciaInventario(pago);
        String motivoSalida = "Pedido marketplace · cotización v" + cotizacion.getVersion();
        List<CotizacionItem> descontados = new ArrayList<>();
        String faltante = descontarPiezasDelTaller(pago, orden, cotizacion, refInventario);
        for (CotizacionItem item : repuestos(cotizacion)) {
            if (faltante != null) break;
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
            reponerPiezasDelTaller(cotizacion, refInventario);
            String sinStock = faltante;
            if (repuestos(cotizacion).stream().anyMatch(i -> i.esDelTaller() && sinStock.equals(i.getProductoNombre()))) {
                avisos.piezaTallerSinStock(cotizacion, faltante);
            }
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
            reponerPiezasDelTaller(cotizacion, refInventario);
            noAplicado(pago, orden, "La solicitud cambió mientras se confirmaba el pago");
            return;
        } catch (RuntimeException e) {
            reponerStock(descontados, refInventario);
            reponerPiezasDelTaller(cotizacion, refInventario);
            throw e;
        }

        // Desde aquí, un fallo reembolsa todo el pago (ver confirmar): ninguna tienda debe despachar lo que se devolvió
        CotizacionRepartida repartida;
        try {
            repartida = repartirCotizacion(pago, orden, pagada, cotizacion);
        } catch (RuntimeException e) {
            cancelarPedidos(pago);
            reponerStock(descontados, refInventario);
            reponerPiezasDelTaller(cotizacion, refInventario);
            devolverOrdenTrasFallo(pago, pagada, cotizacion);
            throw e;
        }

        // El pago ya quedó aplicado: un aviso que falla no puede convertirlo en un reembolso
        avisarPagoAplicado(pago, () -> {
            avisos.comprobantePago(repartida.orden(), pago, "la cotización v" + cotizacion.getVersion() + " de " + cotizacion.getTallerNombre(),
                    lineasCotizacion(cotizacion),
                    repartida.pedidos().isEmpty() ? "El taller ya puede empezar el trabajo."
                            : "Pedimos los repuestos a las tiendas; te avisaremos el avance.");
            avisos.cotizacionPagada(repartida.orden(), repartida.pedidos());
        });
    }

    private record CotizacionRepartida(OrdenServicio orden, List<PedidoTienda> pedidos) {}

    /**
     * El pago se reembolsa y esa cotización ya no vale: como cuando falta stock, se anula y la orden vuelve a DIAGNOSTICADA
     * para que el taller emita otra, sin el pago que no se aplicó. Nunca lanza: si no se puede, se alerta al ADMIN.
     */
    private void devolverOrdenTrasFallo(PagoEntity pago, OrdenServicio pagada, Cotizacion cotizacion) {
        String motivo = "El pago de la cotización no se pudo aplicar: se reembolsa y el taller debe emitir una nueva";
        try {
            transiciones.transicionar(pagada, EstadoOrden.DIAGNOSTICADA, Actor.SISTEMA, motivo,
                    Criteria.where("cotizacionId").is(cotizacion.getId()), new Update().unset("pagoCotizacionId"));
            cotizacionRepository.findById(cotizacion.getId()).ifPresent(c -> {
                c.setEstado(Cotizacion.ANULADA);
                c.setMotivoRespuesta(motivo);
                cotizacionRepository.save(c);
            });
            avisos.pagoNoAplicado(pagada, pago.getMonto(), motivo);
        } catch (RuntimeException e) {
            log.error("No se pudo devolver la orden {} tras fallar el pago {}", pagada.getId(), pago.getReferencia(), e);
            avisos.alertaAdmin("Orden sin corregir", "El pago " + pago.getReferencia() + " no se aplicó y se reembolsa, pero la solicitud "
                    + pagada.getId() + " no pudo volver a DIAGNOSTICADA: corrígela a mano.");
        }
    }

    /**
     * Cancela los pedidos que alcanzaron a crearse para un pago que no se aplicó. Solo si la tienda no los ha despachado;
     * si alguno ya salió, o no se puede cancelar, se alerta al ADMIN. Nunca lanza: el reembolso se registra igual.
     */
    private void cancelarPedidos(PagoEntity pago) {
        try {
            for (PedidoTienda p : pedidoRepository.findByPagoId(pago.getId())) {
                PedidoTienda cancelado = mongoTemplate.findAndModify(
                        Query.query(Criteria.where("id").is(p.getId())
                                .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION)),
                        new Update().set("estado", PedidoTienda.NO_DESPACHADO)
                                .set("fechaNoDespacho", LocalDateTime.now())
                                .set("tipoMotivoNoDespacho", PedidoTienda.MOTIVO_PAGO_NO_APLICADO)
                                .set("motivoNoDespacho", "El pago no se pudo aplicar y se reembolsa al cliente: no despaches este pedido"),
                        FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
                if (cancelado == null) {
                    avisos.alertaAdmin("Pedido sin cancelar", "El pago " + pago.getReferencia() + " no se aplicó y se reembolsa, pero el pedido "
                            + p.getId() + " de " + p.getTiendaNombre() + " ya no estaba pendiente: revísalo con la tienda.");
                }
            }
        } catch (RuntimeException e) {
            log.error("No se pudieron cancelar los pedidos del pago {} que no se aplicó", pago.getReferencia(), e);
            avisos.alertaAdmin("Pedidos sin cancelar", "El pago " + pago.getReferencia() + " no se aplicó y se reembolsa, pero sus pedidos "
                    + "no se pudieron cancelar: avísales a las tiendas que no despachen.");
        }
    }

    private CotizacionRepartida repartirCotizacion(PagoEntity pago, OrdenServicio orden, OrdenServicio pagada, Cotizacion cotizacion) {
        // 3. Pedidos por tienda y reparto
        Taller taller = tallerRepository.findById(orden.getTallerId()).orElse(null);
        List<PedidoTienda> pedidos = crearPedidos(pago, pagada, cotizacion, taller);
        // Montos en pesos enteros y con BigDecimal: lo del taller es el resto exacto del pago, y su base de comisión, el
        // resto de la base: así la comisión de las líneas suma la de todo el pago
        BigDecimal base = ComisionPlataforma.baseCotizacion(cotizacion, pagada, pago.getMonto(), parametros.ivaPct());
        double pct = parametros.comisionPct();
        boolean retener = parametros.comisionRetenida();
        List<RepartoPago> lineas = new ArrayList<>();
        BigDecimal paraTiendas = BigDecimal.ZERO;
        BigDecimal baseTiendas = BigDecimal.ZERO;
        for (PedidoTienda p : pedidos) {
            BigDecimal montoTienda = Dinero.pesos(p.getTotal());
            BigDecimal baseTienda = Dinero.pesos(p.getSubtotal());
            RepartoPago linea = reparto(pago, RepartoPago.TIENDA, p.getTiendaId(), p.getTiendaNombre(),
                    Dinero.aDouble(montoTienda), p.getId());
            ComisionPlataforma.aplicar(linea, baseTienda, pct, retener);
            repartoRepository.save(linea);
            lineas.add(linea);
            paraTiendas = paraTiendas.add(montoTienda);
            baseTiendas = baseTiendas.add(baseTienda);
        }
        BigDecimal paraTaller = Dinero.pesos(pago.getMonto()).subtract(paraTiendas).max(BigDecimal.ZERO);
        if (paraTaller.signum() > 0) {
            RepartoPago linea = reparto(pago, RepartoPago.TALLER, orden.getTallerId(),
                    taller != null ? taller.getNombre() : cotizacion.getTallerNombre(), Dinero.aDouble(paraTaller), null);
            ComisionPlataforma.aplicar(linea, base.subtract(baseTiendas), pct, retener);
            anotarPiezas(linea, cotizacion, pct, retener);
            repartoRepository.save(linea);
            lineas.add(linea);
        }

        // 4. Siguiente estado: con pedidos se esperan; sin ellos (solo mano de obra o todo del inventario), listo para trabajar
        String siguiente = pedidos.isEmpty() ? EstadoOrden.REPUESTOS_RECIBIDOS : EstadoOrden.REPUESTOS_PEDIDOS;
        OrdenServicio actualizada = transiciones.transicionar(pagada, siguiente, Actor.SISTEMA,
                pedidos.isEmpty() ? (repuestos(cotizacion).isEmpty() ? "Cotización solo de mano de obra" : "Repuestos del inventario del taller")
                        : "Repuestos pedidos a " + pedidos.size() + " tienda(s)",
                null, null);

        ComisionPlataforma.registrar(repartoRepository, pago, base, pct, retener, lineas);
        return new CotizacionRepartida(actualizada, pedidos);
    }

    private List<PedidoTienda> crearPedidos(PagoEntity pago, OrdenServicio orden, Cotizacion cotizacion, Taller taller) {
        Map<String, List<CotizacionItem>> porTienda = new LinkedHashMap<>();
        // Lo del taller ya está en el taller: no genera pedido
        for (CotizacionItem item : repuestosDeTiendas(cotizacion)) {
            porTienda.computeIfAbsent(item.getTiendaId(), k -> new ArrayList<>()).add(item);
        }
        // El IVA de la tienda es el de los repuestos (el global), no el de la mano de obra del taller
        double ivaPct = cotizacion.pctRepuestos();
        List<PedidoTienda> pedidos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();
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
                    .plazoDespachoHasta(plazoDespacho(items, ahora, parametros.plazoDespachoDiasHabiles(), parametros.plazoEncargoDiasExtra()))
                    .estado(PedidoTienda.PENDIENTE)
                    .fechaCreacion(ahora)
                    .build()));
        }
        return pedidos;
    }

    /**
     * Plazo máximo de despacho, en días hábiles desde el pago: con todo en stock,
     * diasStock; si el pedido trae encargos, los días estimados del encargo más
     * tardío (sin estimado cuenta 0) más diasExtraEncargo.
     */
    static LocalDateTime plazoDespacho(List<CotizacionItem> items, LocalDateTime pagoEn, int diasStock, int diasExtraEncargo) {
        boolean hayEncargo = false;
        int maxEstimado = 0;
        for (CotizacionItem i : items) {
            if ("ENCARGO".equals(i.getDisponibilidad())) {
                hayEncargo = true;
                if (i.getDiasEntrega() != null) maxEstimado = Math.max(maxEstimado, i.getDiasEntrega());
            }
        }
        return PlazoHabil.sumarDiasHabiles(pagoEn, hayEncargo ? maxEstimado + diasExtraEncargo : diasStock);
    }

    // ─── Reembolsos ──────────────────────────────────────────────────────────

    /** Registra un reembolso pendiente (lo hace el ADMIN a mano en Wompi). */
    public Reembolso registrarReembolso(PagoEntity pago, String pedidoTiendaId, double monto, String motivo) {
        return guardarReembolso(pago, pedidoTiendaId, monto, motivo, ComisionPlataforma.saldoAFavor(repartoRepository, pago, pedidoTiendaId, monto));
    }

    private Reembolso guardarReembolso(PagoEntity pago, String pedidoTiendaId, double monto, String motivo, double saldoAFavor) {
        Reembolso guardado = reembolsoRepository.save(Reembolso.builder()
                .pagoId(pago.getId())
                .ordenServicioId(pago.getOrdenServicioId())
                .compraCatalogoId(pago.getCompraCatalogoId())
                .pedidoTiendaId(pedidoTiendaId)
                .usuarioId(pago.getUsuarioId())
                .referenciaPago(pago.getReferencia())
                .transactionId(pago.getTransactionId())
                .monto(monto)
                .saldoAFavor(saldoAFavor > 0 ? saldoAFavor : null)
                .motivo(motivo.length() > 500 ? motivo.substring(0, 500) : motivo)
                .estado(Reembolso.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build());
        // La comisión del dinero devuelto deja de contar
        ComisionPlataforma.anular(repartoRepository, pago, monto, guardado != null ? guardado.getId() : null, pedidoTiendaId);
        if (saldoAFavor > 0) {
            avisos.alertaAdmin("Reembolso de una línea ya pagada", "El pago " + pago.getReferencia() + " tiene un reembolso de "
                    + Math.round(monto) + " COP sobre dinero que ya se transfirió al negocio. Quedan " + Math.round(saldoAFavor)
                    + " COP a favor de DriveMaster por cobrarle a mano. Motivo: " + motivo);
        }
        return guardado;
    }

    /**
     * Reembolso de una orden cancelada. Sus líneas ya se anularon, y las de los pedidos despachados siguen vigentes y no
     * entran en este dinero: nada de lo devuelto se había transferido, así que no queda saldo a favor.
     */
    Reembolso registrarReembolsoDeCancelacion(PagoEntity pago, double monto, String motivo) {
        return guardarReembolso(pago, null, monto, motivo, 0);
    }

    /**
     * Wompi aprobó la transacción con otro monto o moneda: el pago no se aplica, pero el dinero entró. Se registra un
     * reembolso pendiente por lo que verificó Wompi y se alerta al ADMIN. Sirve para cualquier pago (marketplace,
     * catálogo, solicitudes y ventas). Sin monto reportado por Wompi no hay cifra que devolver: solo la alerta.
     * Nunca lanza: el pago ya quedó marcado MONTO_INVALIDO.
     */
    public void montoInvalido(PagoEntity pago, Long recibidoCentavos, String moneda) {
        String esperado = Math.round(pago.getMonto() != null ? pago.getMonto() : 0) + " COP";
        String recibido = recibidoCentavos != null
                ? Math.round(recibidoCentavos / 100.0) + " " + (moneda != null ? moneda : "(sin moneda)")
                : "un monto que no informó";
        String motivo = "Monto inválido: Wompi reportó " + recibido + " y se esperaban " + esperado
                + " (pago " + pago.getReferencia() + origenDelPago(pago) + ")";
        boolean conReembolso = recibidoCentavos != null && recibidoCentavos > 0;
        try {
            if (conReembolso) registrarReembolso(pago, null, recibidoCentavos / 100.0, motivo);
        } catch (Exception e) {
            conReembolso = false;
            log.error("No se pudo registrar el reembolso del pago {} con monto inválido", pago.getReferencia(), e);
        }
        avisos.alertaAdmin("Pago con monto inválido", motivo + (conReembolso
                ? ". Quedó un reembolso pendiente por " + recibido + ": hazlo en Wompi y márcalo en Liquidaciones."
                : ". No se registró el reembolso: revisa la transacción " + pago.getTransactionId() + " en Wompi."));
    }

    /** Qué se estaba pagando, para ubicarlo desde el reembolso o la alerta. */
    private static String origenDelPago(PagoEntity pago) {
        if (pago.getOrdenServicioId() != null) return ", solicitud " + pago.getOrdenServicioId();
        if (pago.getCompraCatalogoId() != null) return ", compra " + pago.getCompraCatalogoId();
        if (pago.getSolicitudId() != null) return ", encargo " + pago.getSolicitudId();
        if (pago.getVentaId() != null) return ", venta " + pago.getVentaId();
        return "";
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
            if (item.esDelTaller()) {
                if (stockTaller.disponible(cotizacion.getTallerId(), item.getPiezaTallerId()) < item.getCantidad()) return item.getProductoNombre();
                continue;
            }
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
        return !item.esDelTaller() && !"ENCARGO".equals(item.getDisponibilidad()) && item.getProductoId() != null
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

    /**
     * Saca las piezas del inventario del taller, con el rastro de lote y costo. Devuelve el nombre de la que no alcanzó, o
     * null. Nunca toca productos ni pedidos de una tienda.
     */
    private String descontarPiezasDelTaller(PagoEntity pago, OrdenServicio orden, Cotizacion cotizacion, String refInventario) {
        for (CotizacionItem item : repuestos(cotizacion)) {
            if (!item.esDelTaller()) continue;
            try {
                stockTaller.descontar(new StockTallerService.Venta(cotizacion.getTallerId(), item.getPiezaTallerId(), item.getCantidad(),
                        refInventario, item.getPrecioUnitario() != null ? item.getPrecioUnitario() : 0, orden.getId(), cotizacion.getId(),
                        pago.getId(), pago.getUsuarioId()));
            } catch (StockInsuficienteException e) {
                return item.getProductoNombre();
            } catch (Exception e) {
                log.error("Pago {}: no se pudo descontar la pieza {} del taller", pago.getReferencia(), item.getPiezaTallerId(), e);
                return item.getProductoNombre();
            }
        }
        return null;
    }

    /** Devuelve a sus lotes lo que este pago sacó del taller. Nunca lanza: si falla, el reembolso se registra igual y se alerta. */
    private void reponerPiezasDelTaller(Cotizacion cotizacion, String refInventario) {
        if (repuestos(cotizacion).stream().noneMatch(CotizacionItem::esDelTaller)) return;
        try {
            stockTaller.revertir(refInventario, "El pago no se pudo aplicar", null);
        } catch (RuntimeException e) {
            log.error("No se pudieron devolver las piezas del pago {}", refInventario, e);
            avisos.alertaAdmin("Piezas sin devolver", "El pago " + refInventario + " no se aplicó y sus piezas no volvieron al inventario del taller "
                    + cotizacion.getTallerNombre() + ": revísalo a mano.");
        }
    }

    /** Qué parte de la línea del taller son sus repuestos. Se separa aquí porque después solo queda el total de la línea. */
    private static void anotarPiezas(RepartoPago linea, Cotizacion c, double pct, boolean retener) {
        BigDecimal base = Dinero.pesos(c.getSubtotalRepuestosTaller());
        if (base.signum() <= 0) return;
        BigDecimal bruto = base.add(Dinero.pesos(c.getIvaRepuestosTaller()));
        BigDecimal comision = retener ? ComisionPlataforma.monto(base, pct) : BigDecimal.ZERO;
        linea.setPiezasBase(Dinero.aDouble(base));
        linea.setPiezasBruto(Dinero.aDouble(bruto));
        linea.setPiezasNeto(Dinero.aDouble(bruto.subtract(comision).max(BigDecimal.ZERO)));
    }

    /** Solo lo que una tienda debe entregar. Sin tienda no hay pedido posible, venga de donde venga el ítem. */
    private static List<CotizacionItem> repuestosDeTiendas(Cotizacion c) {
        return repuestos(c).stream().filter(i -> !i.esDelTaller() && i.getTiendaId() != null).toList();
    }

    private static List<CotizacionItem> repuestos(Cotizacion c) {
        return c.getItems() == null ? List.of()
                : c.getItems().stream().filter(i -> CotizacionItem.REPUESTO.equals(i.getTipo())).toList();
    }

    private static Map<String, Double> lineasCotizacion(Cotizacion c) {
        Map<String, Double> lineas = new LinkedHashMap<>();
        // Con piezas del inventario el cliente ve de quién es cada parte
        boolean conPiezas = c.getSubtotalRepuestosTaller() != null && c.getSubtotalRepuestosTaller() > 0;
        if (conPiezas) lineas.put("Repuestos del taller", c.getSubtotalRepuestosTaller());
        if (c.getSubtotalRepuestos() != null && c.getSubtotalRepuestos() > 0) {
            lineas.put(conPiezas ? "Repuestos de tiendas" : "Repuestos", c.getSubtotalRepuestos());
        }
        if (c.getSubtotalManoObra() != null && c.getSubtotalManoObra() > 0) lineas.put("Mano de obra", c.getSubtotalManoObra());
        // El comprobante desglosa el IVA igual que la cotización
        CalculadoraCotizacion.lineasIva(c).forEach(l -> lineas.put(l.texto(), l.monto()));
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
                .liquidacionPorEvento(true)
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
