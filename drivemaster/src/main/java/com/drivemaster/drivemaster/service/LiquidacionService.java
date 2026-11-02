package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.exception.RepartoYaEnPagoException;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.model.mysql.ResolucionReparto;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ResolucionRepartoRepository;
import com.drivemaster.drivemaster.service.dispersion.DispersionException;
import com.drivemaster.drivemaster.service.dispersion.DispersionInciertaException;
import com.drivemaster.drivemaster.service.dispersion.DispersionService;
import com.drivemaster.drivemaster.service.dispersion.PagoAnterior;
import com.drivemaster.drivemaster.service.dispersion.ResultadoDispersion;
import com.drivemaster.drivemaster.service.dispersion.SolicitudDispersion;
import com.drivemaster.drivemaster.service.dispersion.WompiPayoutService;
import com.drivemaster.drivemaster.util.Dinero;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * - Un reintento que ya pasó por Wompi le pregunta antes por el intento anterior: si salió, queda
 *   LIQUIDADO sin enviar otro; si no se sabe, no se envía y se resuelve a mano.
 * - Si el evento de Wompi nunca llega, el ADMIN resuelve la línea a mano, con motivo y registro
 *   en resoluciones_reparto. Un pago en curso no se resuelve: solo uno sin confirmar o colgado.
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
    private final ResolucionRepartoRepository resoluciones;

    public LiquidacionService(RepartoPagoRepository repartoRepository,
                              ReembolsoRepository reembolsoRepository,
                              PagoRepository pagoRepository,
                              DispersionService dispersion,
                              AvisosSolicitudServicio avisos,
                              ResolucionRepartoRepository resoluciones) {
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.pagoRepository = pagoRepository;
        this.dispersion = dispersion;
        this.avisos = avisos;
        this.resoluciones = resoluciones;
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
     * desde/hasta sobre la fecha de la línea; en las liquidadas, sobre la fecha en que se pagaron. pendientes y
     * montoPendiente cuentan toda la lista filtrada, no la página.
     */
    public RepartosTabla repartosTabla(String estado, RangoFechas rango, PeticionTabla t) {
        Specification<RepartoPago> spec = (r, q, cb) -> cb.notEqual(r.get("beneficiarioTipo"), RepartoPago.PLATAFORMA);
        if (estado != null && !estado.isBlank()) {
            String e = estado.trim().toUpperCase();
            spec = spec.and((r, q, cb) -> cb.equal(r.get("estado"), e));
        }
        boolean porFechaDePago = estado != null && RepartoPago.LIQUIDADO.equalsIgnoreCase(estado.trim());
        if (rango != null && rango.desde() != null) {
            LocalDateTime d = rango.desde();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(fechaDeLaLinea(r, cb, porFechaDePago), d));
        }
        if (rango != null && rango.antesDe() != null) {
            LocalDateTime a = rango.antesDe();
            spec = spec.and((r, q, cb) -> cb.lessThan(fechaDeLaLinea(r, cb, porFechaDePago), a));
        }
        long total = repartoRepository.count(spec);
        List<RepartoPago> items = repartoRepository.findAll(spec, t.pageRequest(total)).getContent();
        // Lo pendiente son pocas líneas (se van pagando): se suman aquí con el mismo filtro
        List<RepartoPago> pendientes = repartoRepository.findAll(spec.and((r, q, cb) -> r.get("estado").in(ESTADOS_PENDIENTES)));
        double monto = pendientes.stream().mapToDouble(r -> r.getMonto() == null ? 0 : r.getMonto()).sum();
        return new RepartosTabla(t.pagina(items, total), pendientes.size(), monto);
    }

    private static Expression<LocalDateTime> fechaDeLaLinea(Root<RepartoPago> r, CriteriaBuilder cb, boolean porFechaDePago) {
        Path<LocalDateTime> creacion = r.get("fechaCreacion");
        return porFechaDePago ? cb.coalesce(r.<LocalDateTime>get("fechaLiquidacion"), creacion) : creacion;
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
        diagnosticoPrestado(ordenId, "COTIZACION_RECHAZADA_POR_CLIENTE");
    }

    /** La orden se cerró sin reparación, pero el diagnóstico sí se hizo: su línea queda habilitada. Nunca lanza. */
    public void diagnosticoPrestado(String ordenId, String evento) {
        try {
            for (RepartoPago r : lineasDiagnosticoDelTaller(ordenId)) {
                lineaLista(r, evento);
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
            // Con neto 0 no hay nada que aprobar ni transferir, sea cual sea el modo
            if (dispersion.automatica() || sinNadaQueTransferir(reparto)) {
                ejecutar(reparto.getId(), SolicitudDispersion.automatica());
            }
        } catch (Exception e) {
            log.error("No se pudo procesar la liquidación de la línea {} ({})", reparto.getId(), evento, e);
        }
    }

    // ─── Acción del ADMIN ────────────────────────────────────────────────────

    /**
     * Paga una línea (o reintenta una FALLIDO). En modo MANUAL el número de
     * transferencia es obligatorio. Un 409 por línea no tomada corta antes de la dispersión;
     * el de un pago sin confirmar sale después de su único intento: nunca hay un segundo.
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
            throw yaEnPago(resultado, resultado.getMotivoFallo());
        }
        return resultado;
    }

    private static RepartoYaEnPagoException yaEnPago(RepartoPago r, String mensaje) {
        return new RepartoYaEnPagoException(mensaje, r.getEstado(), r.getReferenciaLiquidacion(), r.getMotivoFallo() != null);
    }

    /** Sin motivo de Wompi, una línea PROCESANDO se considera colgada pasado este tiempo. */
    static final int MINUTOS_PARA_RESOLVER = 30;
    private static final int MINIMO_MOTIVO = 10;

    /**
     * Si el ADMIN puede resolver la línea a mano: Wompi no la confirmó, o lleva demasiado tiempo tomada (se cayó el
     * servidor a mitad del pago). Las anteriores a fechaProcesando no tienen hora: cuentan como colgadas.
     */
    public static boolean sePuedeResolver(RepartoPago r, LocalDateTime ahora) {
        if (!RepartoPago.PROCESANDO.equals(r.getEstado())) return false;
        return r.getMotivoFallo() != null || r.getFechaProcesando() == null
                || r.getFechaProcesando().isBefore(ahora.minusMinutes(MINUTOS_PARA_RESOLVER));
    }

    /**
     * Salida manual de una línea PROCESANDO, después de revisar el panel de Wompi: resultado LIQUIDADO (sí salió, con su
     * referencia) o FALLIDO (no salió: se podrá reintentar). La línea conserva la referencia de Wompi, para reconocer
     * un evento tardío. El cambio y su registro van en la misma transacción: no hay uno sin el otro.
     */
    @Transactional
    public RepartoPago resolver(Long id, String correoAdmin, String resultado, String motivo, String referencia) {
        String decision = resultado == null ? "" : resultado.trim().toUpperCase();
        boolean salio = RepartoPago.LIQUIDADO.equals(decision);
        if (!salio && !RepartoPago.FALLIDO.equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica si el pago salió o no salió");
        }
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < MINIMO_MOTIVO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explica en al menos " + MINIMO_MOTIVO + " caracteres por qué se resuelve así");
        }
        String anotada = referencia == null ? "" : referencia.trim();
        if (salio && anotada.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anota la referencia del pago en Wompi");
        }
        if (anotada.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La referencia admite hasta 80 caracteres");
        }

        RepartoPago r = repartoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reparto no encontrado"));
        LocalDateTime ahora = LocalDateTime.now();
        if (!RepartoPago.PROCESANDO.equals(r.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se resuelve una línea en proceso (estado " + r.getEstado() + ")");
        }
        if (!sePuedeResolver(r, ahora)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pago sigue en curso: espera a que Wompi responda "
                    + "o a que pasen " + MINUTOS_PARA_RESOLVER + " minutos");
        }

        String referenciaLinea = r.getReferenciaLiquidacion() != null ? r.getReferenciaLiquidacion() : (anotada.isEmpty() ? null : anotada);
        String modo = r.getModoLiquidacion() != null ? r.getModoLiquidacion() : dispersion.modo();
        // Lo anotado sale del panel de Wompi: es el id con el que la comisión de esta línea cuenta como cobrada
        if (salio && WompiPayoutService.MODO.equals(modo)) {
            referenciaLinea = conIdWompi(r.getReferenciaLiquidacion() != null ? r.getReferenciaLiquidacion() : "WOMPI-" + id, anotada);
        }
        String nota = recortar("Resuelto a mano por " + correoAdmin + ": " + razon
                + (anotada.isEmpty() ? "" : " (ref. " + anotada + ")"), 500);
        int filas = salio
                ? repartoRepository.marcarLiquidado(id, ahora, modo, referenciaLinea, correoAdmin, nota)
                : repartoRepository.marcarFallido(id, recortar("No salió, resuelto a mano: " + razon, 300), modo);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La línea cambió mientras se resolvía: revísala de nuevo");
        }
        resoluciones.save(ResolucionReparto.builder().repartoId(id).resultado(decision).motivo(recortar(razon, 500))
                .referencia(referenciaLinea).resueltoPor(correoAdmin).fecha(ahora).build());
        return repartoRepository.findById(id).orElseThrow();
    }

    // ─── Núcleo ──────────────────────────────────────────────────────────────

    /** Toma la línea, ejecuta la dispersión y deja LIQUIDADO o FALLIDO. Devuelve la línea ya reescrita. */
    private RepartoPago ejecutar(Long id, SolicitudDispersion solicitud) {
        if (repartoRepository.tomarParaLiquidar(id, LocalDateTime.now()) == 0) {
            throw noSePudoTomar(id);
        }
        RepartoPago tomada = repartoRepository.findById(id).orElseThrow();
        if (sinNadaQueTransferir(tomada)) {
            repartoRepository.marcarLiquidado(id, LocalDateTime.now(), RepartoPago.SIN_TRANSFERENCIA, "SIN-TRANSFERENCIA", solicitud.actor(),
                    "Sin transferencia: toda la parte de esta línea fue comisión");
            return repartoRepository.findById(id).orElseThrow();
        }
        if (!sePuedeEnviarOtro(tomada, solicitud)) {
            RepartoPago revisada = repartoRepository.findById(id).orElseThrow();
            if (RepartoPago.LIQUIDADO.equals(revisada.getEstado())) {
                throw yaEnPago(revisada, "Este pago ya había salido en Wompi: no se envió otro");
            }
            return revisada;
        }
        try {
            registrarPago(tomada, dispersion.liquidar(tomada, solicitud), solicitud.actor(), texto(solicitud.nota(), solicitud.actor()));
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

    static final String MOTIVO_SIN_SABER = "No se pudo confirmar en Wompi si el intento anterior salió. Revísalo en su panel "
            + "y resuelve la línea: no se envió otro pago";

    /**
     * Antes de repetir un pago que ya fue a Wompi, pregunta qué pasó con él (la línea ya está tomada: va una sola
     * consulta). Si salió, la deja LIQUIDADO con esa referencia. Si no se sabe, sigue PROCESANDO para resolverla a mano;
     * el ADMIN que la resolvió como no salida ya respondió por ese intento y puede reintentar. true: se puede enviar.
     */
    private boolean sePuedeEnviarOtro(RepartoPago tomada, SolicitudDispersion solicitud) {
        String referencia = tomada.getReferenciaLiquidacion();
        if (referencia == null || !WompiPayoutService.MODO.equals(tomada.getModoLiquidacion())) return true;

        PagoAnterior anterior = consultarPago(referencia);
        switch (anterior.estado()) {
            case NO_SALIO:
                return true;
            case SALIO:
                registrarPago(tomada, new ResultadoDispersion(WompiPayoutService.MODO, conIdWompi(referencia, anterior.idWompi()), Dinero.pesos(tomada.getMonto())),
                        solicitud.actor(), "Wompi confirmó el intento anterior: no se envió otro pago");
                return false;
            default:
                if (resoluciones.existsByResultadoAndReferenciaStartingWith(RepartoPago.FALLIDO, referencia)) {
                    log.warn("Reintento de la línea {} sin confirmar {}: el ADMIN la resolvió como no salida", tomada.getId(), referencia);
                    return true;
                }
                repartoRepository.marcarPorVerificar(tomada.getId(), referencia, MOTIVO_SIN_SABER, WompiPayoutService.MODO);
                avisos.alertaAdmin("Reintento bloqueado", "Revisa en el panel de Wompi el pago de " + tomada.getBeneficiarioNombre()
                        + " (línea " + tomada.getId() + ", referencia " + referencia + "). " + MOTIVO_SIN_SABER);
                return false;
        }
    }

    private PagoAnterior consultarPago(String referencia) {
        try {
            PagoAnterior pago = dispersion.consultarPago(referencia);
            return pago != null ? pago : PagoAnterior.SIN_SABER;
        } catch (RuntimeException e) {
            log.error("Falló la consulta del pago {}", referencia, e);
            return PagoAnterior.SIN_SABER;
        }
    }

    /**
     * El pago ya salió: si guardarlo falla, la línea sigue PROCESANDO con su referencia. Nunca FALLIDO, que se puede
     * reintentar y pagaría dos veces. El evento de Wompi o el ADMIN la cierran.
     */
    private void registrarPago(RepartoPago tomada, ResultadoDispersion r, String actor, String nota) {
        Long id = tomada.getId();
        try {
            if (repartoRepository.marcarLiquidado(id, LocalDateTime.now(), r.modo(), r.referencia(), actor, nota) == 0) {
                log.error("La línea {} dejó de estar PROCESANDO antes de marcarse LIQUIDADA", id);
            }
        } catch (RuntimeException e) {
            log.error("La línea {} se pagó con referencia {} y no se pudo guardar", id, r.referencia(), e);
            try {
                repartoRepository.marcarPorVerificar(id, r.referencia(),
                        "El pago salió, pero no se pudo guardar: resuélvelo con su referencia, no lo reintentes", r.modo());
            } catch (RuntimeException sinBase) {
                log.error("Tampoco se pudo anotar la referencia {} en la línea {}", r.referencia(), id, sinBase);
            }
            avisos.alertaAdmin("Liquidación sin registrar", "El pago de " + tomada.getBeneficiarioNombre() + " (línea " + id + ", "
                    + Math.round(tomada.getMonto()) + " COP, referencia " + r.referencia() + ") salió y no se pudo guardar. No lo reintentes.");
        }
    }

    /** La comisión se llevó toda la línea: pasa con muy poco saldo, si el taller dejó de cobrar IVA tras el diagnóstico. */
    private static boolean sinNadaQueTransferir(RepartoPago r) {
        return r.getMonto() != null && r.getMonto() <= 0 && r.getComisionRetenida() != null && r.getComisionRetenida() > 0;
    }

    private ResponseStatusException noSePudoTomar(Long id) {
        RepartoPago r = repartoRepository.findById(id).orElse(null);
        if (r == null) return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reparto no encontrado");
        if (RepartoPago.LIQUIDADO.equals(r.getEstado())) {
            return yaEnPago(r, "Este reparto ya está liquidado");
        }
        if (RepartoPago.PROCESANDO.equals(r.getEstado())) {
            return yaEnPago(r, r.getMotivoFallo() != null ? r.getMotivoFallo()
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

    /** Patrón del N.º de transacción de Wompi: el mismo que el N.º de transferencia de las liquidaciones MANUAL. */
    static final java.util.regex.Pattern TRANSACCION_VALIDA = java.util.regex.Pattern.compile("[A-Za-z0-9._/\\-]{3,80}");

    /** El ADMIN ya devolvió el dinero en Wompi: queda REALIZADO con el N.º de la transacción del reembolso. */
    public Reembolso marcarReembolsado(Long id, String correoAdmin, String transaccion, String nota) {
        String numero = transaccion == null ? "" : transaccion.trim();
        if (numero.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el número de la transacción del reembolso en Wompi");
        }
        if (!TRANSACCION_VALIDA.matcher(numero).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El número de transacción solo puede llevar letras, números y . _ / - (3 a 80 caracteres)");
        }
        Reembolso r = reembolsoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reembolso no encontrado"));
        if (!Reembolso.PENDIENTE.equals(r.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este reembolso no está pendiente");
        }
        r.setEstado(Reembolso.REALIZADO);
        r.setFechaRealizado(LocalDateTime.now());
        r.setTransaccionReembolso(numero);
        r.setNota(texto(nota, correoAdmin));
        return reembolsoRepository.save(r);
    }

    /** El negocio ya devolvió el saldo a favor de este reembolso: deja de contar como pendiente. */
    public Reembolso marcarSaldoCobrado(Long id, String correoAdmin) {
        if (reembolsoRepository.marcarSaldoCobrado(id, LocalDateTime.now(), correoAdmin) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este reembolso no tiene un saldo pendiente por cobrar");
        }
        return reembolsoRepository.findById(id).orElseThrow();
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
     * Solo una transacción APPROVED confirma el pago. Un lote TOTAL_PAYMENT o PARTIAL_PAYMENT no dice cuál se pagó:
     * se consulta la transacción de la línea, y si sigue en curso no se toca nada.
     */
    public void procesarWebhookPayout(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) return;
        log.info("Evento de Wompi Payouts recibido: {}", body.get("event"));

        Map<?, ?> payout = data.get("payout") instanceof Map<?, ?> p ? p : null;
        Map<?, ?> transaccion = data.get("transaction") instanceof Map<?, ?> t ? t : null;
        Map<?, ?> evento = payout != null ? payout : transaccion;
        if (evento == null || evento.get("status") == null) return;
        String status = String.valueOf(evento.get("status")).toUpperCase();

        String referencia = cadena(evento.get("reference"));
        String payoutId = payout != null ? cadena(payout.get("id")) : cadena(transaccion.get("payoutId"));

        RepartoPago reparto = lineaDelPayout(referencia, payoutId).orElse(null);
        // La línea se reintentó con otra referencia después de darla por no salida: pudo pagarse dos veces
        boolean reemplazado = reparto == null && resueltaComoNoSalida(referencia, payoutId);
        if (reparto == null) log.warn("Evento de Wompi Payouts sin línea de reparto: {}", evento.get("id"));
        if (reparto == null && !reemplazado) return;

        boolean hablaDeLaTransaccion = payout == null || LOTES_TERMINADOS.contains(status);
        if (payout != null && hablaDeLaTransaccion) {
            status = estadoDeSuTransaccion(referencia != null || reparto == null ? referencia : reparto.getReferenciaLiquidacion(), payoutId);
        }
        boolean confirmado = hablaDeLaTransaccion && TRANSACCION_PAGADA.equals(status);

        if (reparto == null) {
            if (confirmado) {
                avisos.alertaAdmin("Posible doble pago", "Wompi confirmó el pago " + (referencia != null ? referencia : payoutId)
                        + ", que se había resuelto a mano como no salido y ya no es el intento vigente de su línea. "
                        + "Revísalo en el panel de Wompi.");
            }
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
        } else if (confirmado) {
            log.info("Wompi Payout confirmado para reparto #{}", reparto.getId());
            String conId = conIdWompi(reparto.getReferenciaLiquidacion(), payoutId);
            if (RepartoPago.PROCESANDO.equals(reparto.getEstado())) {
                repartoRepository.marcarLiquidado(reparto.getId(), LocalDateTime.now(), "WOMPI",
                        conId, "WOMPI_WEBHOOK", "Confirmado vía webhook Wompi ACH");
            } else if (RepartoPago.LIQUIDADO.equals(reparto.getEstado())) {
                // La respuesta al crear el pago no trajo el id: lo trae el evento
                if (!java.util.Objects.equals(conId, reparto.getReferenciaLiquidacion())) repartoRepository.anotarReferencia(reparto.getId(), conId);
            } else if (RepartoPago.FALLIDO.equals(reparto.getEstado())
                    && resoluciones.existsByRepartoIdAndResultado(reparto.getId(), RepartoPago.FALLIDO)
                    && repartoRepository.confirmarPagoTardio(reparto.getId(), LocalDateTime.now(), "WOMPI_WEBHOOK",
                            "Wompi confirmó el pago después de resolverlo a mano como no salido") > 0) {
                // Solo con una resolución manual: un FALLIDO del banco no se revive con un evento fuera de orden
                avisos.alertaAdmin("Pago confirmado tarde", "Wompi confirmó el pago de " + reparto.getBeneficiarioNombre()
                        + " (reparto #" + reparto.getId() + ") que se había resuelto como no salido. La línea queda "
                        + "liquidada: no la reintentes.");
            }
        } else {
            log.info("Estado de Wompi Payouts sin acción para reparto #{}: {}", reparto.getId(), status);
        }
    }

    /** La referencia de la línea con el id de Wompi, como la deja WompiPayoutService. Si ya lo tiene o no cabe, no cambia. */
    static String conIdWompi(String referencia, String idWompi) {
        if (referencia == null || idWompi == null || idWompi.isBlank() || referencia.contains("[ID:")) return referencia;
        String conId = referencia + " [ID:" + idWompi + "]";
        return conId.length() > 80 ? referencia : conId;
    }

    private static final Set<String> RECHAZOS_PAYOUT = Set.of("REJECTED", "FAILED", "ERROR", "DECLINED", "REVERSED", "CANCELLED", "CANCELED");
    private static final String TRANSACCION_PAGADA = "APPROVED";
    private static final Set<String> LOTES_TERMINADOS = Set.of("TOTAL_PAYMENT", "PARTIAL_PAYMENT");

    /** El estado que decide por un lote terminado: el de su transacción. SIN_SABER no es ninguno conocido: no cambia nada. */
    private String estadoDeSuTransaccion(String referencia, String payoutId) {
        String sinId = referencia != null && referencia.contains(" [ID:") ? referencia.substring(0, referencia.indexOf(" [ID:")) : referencia;
        return switch (consultarPago(conIdWompi(sinId, payoutId)).estado()) {
            case SALIO -> TRANSACCION_PAGADA;
            case NO_SALIO -> "FAILED";
            case SIN_SABER -> "SIN_SABER";
        };
    }

    private java.util.Optional<RepartoPago> lineaDelPayout(String referencia, String payoutId) {
        java.util.Optional<RepartoPago> porReferencia = referencia != null
                ? repartoRepository.findFirstByReferenciaLiquidacionStartingWith(referencia) : java.util.Optional.empty();
        if (porReferencia.isPresent() || payoutId == null) return porReferencia;
        return repartoRepository.findFirstByReferenciaLiquidacionEndingWith("[ID:" + payoutId + "]");
    }

    private boolean resueltaComoNoSalida(String referencia, String payoutId) {
        return (referencia != null && resoluciones.existsByResultadoAndReferenciaStartingWith(RepartoPago.FALLIDO, referencia))
                || (payoutId != null && resoluciones.existsByResultadoAndReferenciaEndingWith(RepartoPago.FALLIDO, "[ID:" + payoutId + "]"));
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
