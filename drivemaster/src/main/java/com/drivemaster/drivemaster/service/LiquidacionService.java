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

/**
 * Liquidación a talleres y tiendas (y reembolsos a clientes).
 *
 * Ciclo de una línea de reparto (RepartoPago):
 *   PENDIENTE ──evento──▶ POR_LIQUIDAR ──tomar──▶ PROCESANDO ──▶ LIQUIDADO
 *                                  ▲                  └──────▶ FALLIDO ──reintento──┐
 *                                  └─────────────────────────────────────────────────┘
 * - Evento: la tienda, al confirmar el taller la recepción (o al validar el cliente el
 *   retiro); el taller, cuando la orden queda ENTREGADA.
 * - Modo SIMULADO: el evento liquida solo. Modo MANUAL: la línea espera en POR_LIQUIDAR
 *   hasta que el ADMIN paga por el banco y anota el número de transferencia.
 * - Cada paso es una escritura condicional (UPDATE ... WHERE estado = ...): dos clics, o
 *   el evento y el ADMIN a la vez, no la pagan dos veces.
 * - Una línea con reembolso pendiente (de su pedido o de todo el pago) no se toma.
 * - Las líneas anteriores a la Fase 4-B (liquidacionPorEvento nulo) siguen el flujo
 *   anterior: el ADMIN las paga desde PENDIENTE.
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
        return Map.of("modo", dispersion.modo(), "automatica", dispersion.automatica());
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
        if (RepartoPago.FALLIDO.equals(resultado.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo liquidar: " + resultado.getMotivoFallo() + ". Puedes reintentar.");
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
            return new ResponseStatusException(HttpStatus.CONFLICT, "Este reparto se está liquidando en este momento");
        }
        if (RepartoPago.ANULADO.equals(r.getEstado())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Este reparto está anulado");
        }
        if (RepartoPago.PENDIENTE.equals(r.getEstado()) && Boolean.TRUE.equals(r.getLiquidacionPorEvento())) {
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "Todavía no se cumple la condición para liquidar esta línea (recepción confirmada u orden entregada)");
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

    private static String recortar(String t, int max) {
        return t != null && t.length() > max ? t.substring(0, max) : t;
    }
}
