package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.dispersion.DispersionException;
import com.drivemaster.drivemaster.service.dispersion.DispersionInciertaException;
import com.drivemaster.drivemaster.service.dispersion.DispersionService;
import com.drivemaster.drivemaster.service.dispersion.ResultadoDispersion;
import com.drivemaster.drivemaster.service.dispersion.SolicitudDispersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liquidación a talleres y tiendas (y reembolsos a clientes).
 *
 * Ciclo de una línea de reparto (RepartoPago):
 *   PENDIENTE ──evento──▶ POR_LIQUIDAR ──tomar──▶ PROCESANDO ──▶ LIQUIDADO
 *                                  ▲                  └──────▶ FALLIDO ──reintento──┐
 *                                  └─────────────────────────────────────────────────┘
 * - Evento: la tienda, al confirmar el taller la recepción (o al validar el cliente el
 *   retiro); el taller, cuando la orden queda ENTREGADA.
 * - Modo SIMULADO: el evento liquida solo. Modos MANUAL y WOMPI: la línea espera en
 *   POR_LIQUIDAR hasta que el ADMIN la aprueba (en MANUAL paga por el banco y anota el número
 *   de transferencia).
 * - Si Wompi no confirma un pago ya enviado, la línea sigue PROCESANDO: reintentarla podría
 *   pagar dos veces. La resuelve el evento de Wompi.
 * - Cada paso es una escritura condicional (UPDATE ... WHERE estado = ...): dos clics, o
 *   el evento y el ADMIN a la vez, no la pagan dos veces.
 * - Una línea con reembolso pendiente (de su pedido o de todo el pago) no se toma.
 * - Una línea PENDIENTE nunca se paga: tampoco las anteriores a la Fase 4-B
 *   (liquidacionPorEvento nulo), que además el evento no habilita.
 */
@Service
public class LiquidacionService {

    private static final Logger log = LoggerFactory.getLogger(LiquidacionService.class);

    private final RepartoPagoRepository repartoRepository;
    private final ReembolsoRepository reembolsoRepository;
    private final PagoRepository pagoRepository;
    private final DispersionService dispersion;
    private final AvisosSolicitudServicio avisos;

    public LiquidacionService(RepartoPagoRepository repartoRepository,
                              ReembolsoRepository reembolsoRepository,
                              PagoRepository pagoRepository,
                              DispersionService dispersion,
                              AvisosSolicitudServicio avisos) {
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.pagoRepository = pagoRepository;
        this.dispersion = dispersion;
        this.avisos = avisos;
    }

    public List<RepartoPago> repartos(String estado) {
        List<RepartoPago> lista = estado != null && !estado.isBlank()
                ? repartoRepository.findByEstadoOrderByFechaCreacionDesc(estado.trim().toUpperCase())
                : repartoRepository.findAllByOrderByFechaCreacionDesc();
        // La comisión de la plataforma es informativa: no hay nada que liquidar
        return lista.stream().filter(r -> !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).toList();
    }

    /** Liquidaciones: por defecto las más recientes; también por fecha, monto, estado y beneficiario. */
    public static final PeticionTabla.OrdenPermitido ORDEN_REPARTOS = PeticionTabla.OrdenPermitido.de(
            Sort.by(Sort.Direction.DESC, "fechaCreacion").and(Sort.by(Sort.Direction.DESC, "id")),
            "fecha", "fechaCreacion", "monto", "monto", "estado", "estado", "beneficiario", "beneficiarioNombre")
            .conDesempate("id");

    /** Estados que todavía se tienen que pagar (lo que suma el indicador "Pendiente en esta lista"). */
    static final List<String> ESTADOS_PENDIENTES = List.of(RepartoPago.PENDIENTE, RepartoPago.POR_LIQUIDAR, RepartoPago.FALLIDO);

    /**
     * La tabla paginada de liquidaciones, con los mismos filtros que repartos() (sin la comisión de la plataforma) y
     * desde/hasta sobre la fecha de la línea. pendientes y montoPendiente cuentan toda la lista filtrada, no la página.
     */
    public RepartosTabla repartosTabla(String estado, RangoFechas rango, PeticionTabla t) {
        Specification<RepartoPago> spec = (r, q, cb) -> cb.notEqual(r.get("beneficiarioTipo"), RepartoPago.PLATAFORMA);
        if (estado != null && !estado.isBlank()) {
            String e = estado.trim().toUpperCase();
            spec = spec.and((r, q, cb) -> cb.equal(r.get("estado"), e));
        }
        if (rango != null && rango.desde() != null) {
            LocalDateTime d = rango.desde();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fechaCreacion"), d));
        }
        if (rango != null && rango.antesDe() != null) {
            LocalDateTime a = rango.antesDe();
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("fechaCreacion"), a));
        }
        long total = repartoRepository.count(spec);
        List<RepartoPago> items = repartoRepository.findAll(spec, t.pageRequest(total)).getContent();
        // Lo pendiente son pocas líneas (se van pagando): se suman aquí con el mismo filtro
        List<RepartoPago> pendientes = repartoRepository.findAll(spec.and((r, q, cb) -> r.get("estado").in(ESTADOS_PENDIENTES)));
        double monto = pendientes.stream().mapToDouble(r -> r.getMonto() == null ? 0 : r.getMonto()).sum();
        return new RepartosTabla(t.pagina(items, total), pendientes.size(), monto);
    }

    /** repartos: la página; pendientes y montoPendiente: de toda la lista con estos filtros. */
    public record RepartosTabla(Pagina<RepartoPago> repartos, long pendientes, double montoPendiente) {}

    /** Modo de dispersión vigente, para mostrarlo en la pantalla del ADMIN. */
    public Map<String, Object> modo() {
        return Map.of("modo", dispersion.modo(), "automatica", dispersion.automatica(), "dineroReal", dispersion.dineroReal());
    }

    // ─── Eventos que habilitan el pago ───────────────────────────────────────

    /** La tienda ya puede cobrar este pedido (recepción confirmada por el taller, retiro validado...). */
    public void pedidoRecibido(String pedidoTiendaId, String evento) {
        for (RepartoPago r : repartoRepository.findByPedidoTiendaId(pedidoTiendaId)) {
            if (RepartoPago.TIENDA.equals(r.getBeneficiarioTipo())) lineaLista(r, evento);
        }
    }

    /** El taller ya puede cobrar: la orden quedó ENTREGADA. */
    public void ordenEntregada(String ordenId) {
        for (RepartoPago r : repartoRepository.findByOrdenServicioIdAndBeneficiarioTipo(ordenId, RepartoPago.TALLER)) {
            lineaLista(r, "ORDEN_ENTREGADA");
        }
    }

    /**
     * El cliente rechazó la cotización después del diagnóstico y la orden quedó CANCELADA: el
     * taller sí hizo el diagnóstico, así que su línea del diagnóstico queda habilitada para
     * liquidar (decisión 17). Nunca lanza.
     */
    public void ordenCanceladaPorRechazoDeCotizacion(String ordenId) {
        try {
            for (RepartoPago r : lineasDiagnosticoDelTaller(ordenId)) {
                lineaLista(r, "COTIZACION_RECHAZADA_POR_CLIENTE");
            }
        } catch (Exception e) {
            log.error("No se pudo habilitar la liquidación del diagnóstico de la orden {}", ordenId, e);
        }
    }

    /**
     * La solicitud quedó RECHAZADA porque ningún taller la atendió: el diagnóstico no se
     * prestó, así que la línea del taller no se liquida (se anula) y el cliente recibe un
     * reembolso pendiente por lo que pagó. Nunca lanza.
     */
    public void ordenRechazadaSinTaller(String ordenId) {
        try {
            for (RepartoPago r : lineasDiagnosticoDelTaller(ordenId)) {
                if (!RepartoPago.PENDIENTE.equals(r.getEstado())) continue;
                if (repartoRepository.anularPendiente(r.getId(),
                        "Ningún taller atendió la solicitud: el diagnóstico no se prestó") == 0) continue;
                pagoRepository.findById(r.getPagoId()).ifPresent(pago -> {
                    // Si el pago ya tiene un reembolso registrado (ej. por no aplicarse) no se duplica
                    if (reembolsoRepository.existsByPagoId(pago.getId())) return;
                    Reembolso guardado = reembolsoRepository.save(Reembolso.builder()
                            .pagoId(pago.getId())
                            .ordenServicioId(pago.getOrdenServicioId())
                            .usuarioId(pago.getUsuarioId())
                            .referenciaPago(pago.getReferencia())
                            .transactionId(pago.getTransactionId())
                            .monto(pago.getMonto())
                            .motivo("Ningún taller atendió la solicitud: se reembolsa el diagnóstico")
                            .estado(Reembolso.PENDIENTE)
                            .fechaCreacion(LocalDateTime.now())
                            .build());
                    // El diagnóstico no se prestó: su comisión deja de contar
                    ComisionPlataforma.anular(repartoRepository, pago, pago.getMonto(),
                            guardado != null ? guardado.getId() : null, null);
                    avisos.alertaAdmin("Reembolso pendiente", "La solicitud " + ordenId
                            + " quedó sin taller. Reembolsar " + Math.round(pago.getMonto())
                            + " COP del diagnóstico (pago " + pago.getReferencia() + ").");
                });
            }
        } catch (Exception e) {
            log.error("No se pudo anular la liquidación del diagnóstico de la orden {}", ordenId, e);
        }
    }

    private List<RepartoPago> lineasDiagnosticoDelTaller(String ordenId) {
        return repartoRepository.findByOrdenServicioIdAndBeneficiarioTipo(ordenId, RepartoPago.TALLER).stream()
                .filter(r -> "DIAGNOSTICO".equals(r.getConcepto())).toList();
    }

    /**
     * PENDIENTE → POR_LIQUIDAR y, si la dispersión es automática, la paga. Nunca lanza:
     * un problema aquí no debe deshacer la acción del taller o de la tienda que lo disparó.
     */
    void lineaLista(RepartoPago reparto, String evento) {
        try {
            if (!RepartoPago.PENDIENTE.equals(reparto.getEstado())) return;
            if (repartoRepository.marcarPorLiquidar(reparto.getId(), LocalDateTime.now(), evento) == 0) return;
            if (dispersion.automatica()) {
                ejecutar(reparto.getId(), SolicitudDispersion.automatica());
            }
        } catch (Exception e) {
            log.error("No se pudo procesar la liquidación de la línea {} ({})", reparto.getId(), evento, e);
        }
    }

    // ─── Acción del ADMIN ────────────────────────────────────────────────────

    /**
     * Paga una línea (o reintenta una FALLIDO). En modo MANUAL el número de
     * transferencia es obligatorio.
     */
    public RepartoPago liquidar(Long id, String correoAdmin, String numeroTransferencia, String nota) {
        SolicitudDispersion solicitud = new SolicitudDispersion(correoAdmin, numeroTransferencia, nota);
        dispersion.validar(solicitud);
        RepartoPago resultado = ejecutar(id, solicitud);
        // 422 y no 502: un proxy puede reemplazar un 502 por su propia página, y el mensaje no llegaría a la pantalla
        if (RepartoPago.FALLIDO.equals(resultado.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo liquidar: " + resultado.getMotivoFallo() + ". Puedes reintentar.");
        }
        if (RepartoPago.PROCESANDO.equals(resultado.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, resultado.getMotivoFallo());
        }
        return resultado;
    }

    // ─── Núcleo ──────────────────────────────────────────────────────────────

    /** Toma la línea, ejecuta la dispersión y deja LIQUIDADO o FALLIDO. Devuelve la línea ya reescrita. */
    private RepartoPago ejecutar(Long id, SolicitudDispersion solicitud) {
        if (repartoRepository.tomarParaLiquidar(id) == 0) {
            throw noSePudoTomar(id);
        }
        RepartoPago tomada = repartoRepository.findById(id).orElseThrow();
        try {
            ResultadoDispersion r = dispersion.liquidar(tomada, solicitud);
            int filas = repartoRepository.marcarLiquidado(id, LocalDateTime.now(), r.modo(), r.referencia(),
                    solicitud.actor(), texto(solicitud.nota(), solicitud.actor()));
            if (filas == 0) {
                log.error("La línea {} dejó de estar PROCESANDO antes de marcarse LIQUIDADA", id);
            }
        } catch (DispersionInciertaException e) {
            String motivo = recortar(e.getMessage(), 300);
            repartoRepository.marcarPorVerificar(id, e.getReferencia(), motivo, dispersion.modo());
            avisos.alertaAdmin("Liquidación sin confirmar", "Revisa en el panel de Wompi el pago de "
                    + tomada.getBeneficiarioNombre() + " (línea " + id + ", " + Math.round(tomada.getMonto()) + " COP, referencia "
                    + e.getReferencia() + "). " + motivo);
        } catch (RuntimeException e) {
            String motivo = e instanceof DispersionException ? e.getMessage()
                    : "Error inesperado: " + e.getClass().getSimpleName();
            if (!(e instanceof DispersionException)) log.error("Fallo inesperado liquidando la línea {}", id, e);
            repartoRepository.marcarFallido(id, recortar(motivo, 300), dispersion.modo());
            avisos.alertaAdmin("Liquidación fallida", "La liquidación de " + tomada.getBeneficiarioNombre()
                    + " (línea " + id + ", " + Math.round(tomada.getMonto()) + " COP) falló: " + motivo
                    + ". Reintenta desde Liquidaciones.");
        }
        return repartoRepository.findById(id).orElseThrow();
    }

    private ResponseStatusException noSePudoTomar(Long id) {
        RepartoPago r = repartoRepository.findById(id).orElse(null);
        if (r == null) return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reparto no encontrado");
        if (RepartoPago.LIQUIDADO.equals(r.getEstado())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Este reparto ya está liquidado");
        }
        if (RepartoPago.PROCESANDO.equals(r.getEstado())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, r.getMotivoFallo() != null ? r.getMotivoFallo()
                    : "Este reparto se está liquidando en este momento");
        }
        if (RepartoPago.ANULADO.equals(r.getEstado())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Este reparto está anulado");
        }
        if (RepartoPago.PENDIENTE.equals(r.getEstado())) {
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "Todavía no se puede liquidar: falta confirmar la recepción del pedido o la entrega de la orden");
        }
        if (reembolsoRepository.hayPendienteQueAfecta(r.getPagoId(), r.getPedidoTiendaId())) {
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta línea tiene un reembolso pendiente: no se liquida hasta resolverlo");
        }
        return new ResponseStatusException(HttpStatus.CONFLICT, "Este reparto no se puede liquidar (estado " + r.getEstado() + ")");
    }

    // ─── Reembolsos ──────────────────────────────────────────────────────────

    public List<Reembolso> reembolsos(String estado) {
        return estado != null && !estado.isBlank()
                ? reembolsoRepository.findByEstadoOrderByFechaCreacionDesc(estado.trim().toUpperCase())
                : reembolsoRepository.findAllByOrderByFechaCreacionDesc();
    }

    public Reembolso marcarReembolsado(Long id, String correoAdmin, String nota) {
        Reembolso r = reembolsoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reembolso no encontrado"));
        if (!Reembolso.PENDIENTE.equals(r.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este reembolso no está pendiente");
        }
        r.setEstado(Reembolso.REALIZADO);
        r.setFechaRealizado(LocalDateTime.now());
        r.setNota(texto(nota, correoAdmin));
        return reembolsoRepository.save(r);
    }

    private static String texto(String nota, String actor) {
        String t = "Marcado por " + actor + (nota != null && !nota.isBlank() ? ": " + nota.trim() : "");
        return recortar(t, 500);
    }

    /**
     * Evento de Wompi Pagos a Terceros, ya con la firma verificada (FirmaEventosPayout): payout.updated trae
     * data.payout y transaction.updated trae data.transaction, que nombra su payout con payoutId. Una transferencia
     * rechazada deja la línea FALLIDO aunque ya estuviera LIQUIDADO. La línea solo se busca por la referencia del
     * intento vigente: el evento de un intento anterior ya no la encuentra y no deshace un reintento pagado.
     */
    public void procesarWebhookPayout(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) return;
        log.info("Evento de Wompi Payouts recibido: {}", body.get("event"));

        Map<?, ?> payout = data.get("payout") instanceof Map<?, ?> p ? p : null;
        Map<?, ?> transaccion = data.get("transaction") instanceof Map<?, ?> t ? t : null;
        Map<?, ?> evento = payout != null ? payout : transaccion;
        if (evento == null || evento.get("status") == null) return;
        String status = String.valueOf(evento.get("status")).toUpperCase();

        RepartoPago reparto = (payout != null ? lineaDelPayout(cadena(payout.get("reference")), cadena(payout.get("id")))
                : lineaDelPayout(null, cadena(transaccion.get("payoutId")))).orElse(null);
        if (reparto == null) {
            log.warn("Evento de Wompi Payouts sin línea de reparto: {}", evento.get("id"));
            return;
        }

        if (RECHAZOS_PAYOUT.contains(status)) {
            String motivo = motivoRechazo(evento);
            log.warn("Wompi Payout rechazado para reparto #{}: {}", reparto.getId(), motivo);
            // 0: ya estaba FALLIDO (Wompi reintenta los eventos): no se alerta dos veces
            if (repartoRepository.marcarFallidoAsincrono(reparto.getId(), recortar("Rechazo bancario Wompi ACH: " + motivo, 300)) > 0) {
                avisos.alertaAdmin("Liquidación rechazada por banco", "La dispersión Wompi para "
                        + reparto.getBeneficiarioNombre() + " (reparto #" + reparto.getId() + ") falló en la entidad bancaria: "
                        + motivo + ". Revisa la cuenta bancaria del negocio y reintenta desde Liquidaciones.");
            }
        } else if (CONFIRMACIONES_PAYOUT.contains(status)) {
            log.info("Wompi Payout confirmado para reparto #{}", reparto.getId());
            if (RepartoPago.PROCESANDO.equals(reparto.getEstado())) {
                repartoRepository.marcarLiquidado(reparto.getId(), LocalDateTime.now(), "WOMPI",
                        reparto.getReferenciaLiquidacion(), "WOMPI_WEBHOOK", "Confirmado vía webhook Wompi ACH");
            }
        } else {
            log.info("Estado de Wompi Payouts sin acción para reparto #{}: {}", reparto.getId(), status);
        }
    }

    private static final Set<String> RECHAZOS_PAYOUT = Set.of("REJECTED", "FAILED", "ERROR", "DECLINED", "REVERSED", "CANCELED");
    private static final Set<String> CONFIRMACIONES_PAYOUT = Set.of("APPROVED", "PROCESSED", "SUCCESSFUL", "TOTAL_PAYMENT", "PAID", "SUCCESS");

    private java.util.Optional<RepartoPago> lineaDelPayout(String referencia, String payoutId) {
        java.util.Optional<RepartoPago> porReferencia = referencia != null
                ? repartoRepository.findFirstByReferenciaLiquidacionStartingWith(referencia) : java.util.Optional.empty();
        if (porReferencia.isPresent() || payoutId == null) return porReferencia;
        return repartoRepository.findFirstByReferenciaLiquidacionEndingWith("[ID:" + payoutId + "]");
    }

    /** failureReason es { code, message } en los eventos de transacción. */
    private static String motivoRechazo(Map<?, ?> evento) {
        Object motivo = evento.get("failureReason");
        if (motivo instanceof Map<?, ?> m) motivo = m.get("message") != null ? m.get("message") : m.get("code");
        return motivo != null ? String.valueOf(motivo) : "Transferencia rechazada por entidad bancaria";
    }

    private static String cadena(Object valor) {
        return valor != null && !String.valueOf(valor).isBlank() ? String.valueOf(valor) : null;
    }

    private static String recortar(String t, int max) {
        return t != null && t.length() > max ? t.substring(0, max) : t;
    }
}
