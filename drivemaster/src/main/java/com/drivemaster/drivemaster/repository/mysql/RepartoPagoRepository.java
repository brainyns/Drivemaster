package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// JpaSpecificationExecutor: la tabla paginada de Liquidaciones arma sus filtros opcionales (estado, fechas) sin JPQL
public interface RepartoPagoRepository extends JpaRepository<RepartoPago, Long>, JpaSpecificationExecutor<RepartoPago> {
    List<RepartoPago> findAllByOrderByFechaCreacionDesc();
    List<RepartoPago> findByEstadoOrderByFechaCreacionDesc(String estado);
    List<RepartoPago> findByPedidoTiendaId(String pedidoTiendaId);
    List<RepartoPago> findByPagoId(Long pagoId);
    List<RepartoPago> findByOrdenServicioId(String ordenServicioId);
    boolean existsByPagoIdAndBeneficiarioTipo(Long pagoId, String beneficiarioTipo);
    long countByEstado(String estado);

    boolean existsByPagoIdAndBeneficiarioTipoAndReferenciaLiquidacion(Long pagoId, String beneficiarioTipo, String referencia);

    /** Comisión vigente de un pago: la registrada menos lo anulado por reembolsos. */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.pagoId = :pagoId and r.beneficiarioTipo = 'PLATAFORMA'")
    double sumaComisionDePago(@Param("pagoId") Long pagoId);

    /** Comisión originalmente registrada de un pago (sin descontar anulaciones). */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.pagoId = :pagoId and r.beneficiarioTipo = 'PLATAFORMA' and r.monto > 0")
    double sumaComisionBruta(@Param("pagoId") Long pagoId);

    /** Lo repartido a cada taller, antes de comisión y sin lo anulado: [beneficiarioId, suma]. */
    @Query("select r.beneficiarioId, sum(coalesce(r.montoBruto, r.monto)) from RepartoPago r "
            + "where r.beneficiarioTipo = 'TALLER' and r.estado <> 'ANULADO' group by r.beneficiarioId")
    List<Object[]> sumaRepartidaPorTaller();

    /** Resumen de lo repartido a un beneficiario por estado: [estado, líneas, suma]. */
    @Query("select r.estado, count(r), sum(r.monto) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id group by r.estado")
    List<Object[]> resumenPorEstado(@Param("tipo") String tipo, @Param("id") String id);

    /** Lo repartido a un beneficiario por concepto y estado: [concepto, estado, líneas, suma]. */
    @Query("select r.concepto, r.estado, count(r), sum(r.monto), sum(coalesce(r.piezasNeto, 0)) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id group by r.concepto, r.estado")
    List<Object[]> resumenPorConceptoYEstado(@Param("tipo") String tipo, @Param("id") String id);

    /**
     * Lo repartido a un beneficiario en un periodo, por concepto y estado: [concepto, estado, líneas, suma]. Cada línea
     * cuenta una sola vez, en su estado actual: la liquidada por la fecha en que se pagó (las antiguas sin esa fecha, por
     * la de creación) y las demás por la fecha en que se creó (cuando el cliente pagó).
     */
    @Query("select r.concepto, r.estado, count(r), sum(r.monto), sum(coalesce(r.piezasNeto, 0)) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id and "
            + "((r.estado = 'LIQUIDADO' and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde) "
            + "or (r.estado <> 'LIQUIDADO' and r.fechaCreacion >= :desde)) "
            + "group by r.concepto, r.estado")
    List<Object[]> resumenPorConceptoYEstadoDesde(@Param("tipo") String tipo, @Param("id") String id,
                                                  @Param("desde") LocalDateTime desde);

    /**
     * Lo liquidado a un beneficiario desde una fecha, línea por línea, para la serie por mes o semana:
     * [fecha en que se liquidó (las antiguas sin esa fecha: la de creación), monto].
     */
    @Query("select coalesce(r.fechaLiquidacion, r.fechaCreacion), r.monto from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id and r.estado = 'LIQUIDADO' "
            + "and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde")
    List<Object[]> liquidadoDesde(@Param("tipo") String tipo, @Param("id") String id, @Param("desde") LocalDateTime desde);

    /** Totales de un negocio sin lo anulado: [venta, comisión descontada, comisión calculada, lo que recibe]. */
    @Query("select coalesce(sum(coalesce(r.montoBruto, r.monto)), 0), coalesce(sum(coalesce(r.comisionRetenida, 0)), 0), "
            + "coalesce(sum(coalesce(r.comisionCalculada, 0)), 0), coalesce(sum(r.monto), 0) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id and r.estado <> 'ANULADO'")
    List<Object[]> desgloseDeNegocio(@Param("tipo") String tipo, @Param("id") String id);

    /** Lo mismo en un periodo, con las fechas de resumenPorConceptoYEstadoDesde. */
    @Query("select coalesce(sum(coalesce(r.montoBruto, r.monto)), 0), coalesce(sum(coalesce(r.comisionRetenida, 0)), 0), "
            + "coalesce(sum(coalesce(r.comisionCalculada, 0)), 0), coalesce(sum(r.monto), 0) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id and r.estado <> 'ANULADO' and "
            + "((r.estado = 'LIQUIDADO' and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde) "
            + "or (r.estado <> 'LIQUIDADO' and r.fechaCreacion >= :desde))")
    List<Object[]> desgloseDeNegocioDesde(@Param("tipo") String tipo, @Param("id") String id, @Param("desde") LocalDateTime desde);

    /** Los pedidos de tienda de las líneas de un periodo (mismas fechas que desgloseDeNegocioDesde), para sumar su IVA. */
    @Query("select distinct r.pedidoTiendaId from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id and r.estado <> 'ANULADO' and r.pedidoTiendaId is not null and "
            + "((r.estado = 'LIQUIDADO' and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde) "
            + "or (r.estado <> 'LIQUIDADO' and r.fechaCreacion >= :desde))")
    List<String> pedidosDeNegocioDesde(@Param("tipo") String tipo, @Param("id") String id, @Param("desde") LocalDateTime desde);

    /**
     * Para el total de un periodo, con las mismas fechas: [órdenes con líneas, valor sin IVA, líneas antiguas sin el dato].
     * El valor sin IVA es bruto − ivaIncluido; en las líneas anteriores a la comisión con IVA, su base (era sin IVA).
     */
    @Query("select count(distinct r.ordenServicioId), coalesce(sum(case when r.ivaIncluido is not null "
            + "then coalesce(r.montoBruto, r.monto) - r.ivaIncluido else r.comisionBase end), 0), "
            + "coalesce(sum(case when r.ivaIncluido is null and r.comisionBase is null then 1 else 0 end), 0) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id and r.estado <> 'ANULADO' and "
            + "((r.estado = 'LIQUIDADO' and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde) "
            + "or (r.estado <> 'LIQUIDADO' and r.fechaCreacion >= :desde))")
    List<Object[]> basePorOrdenesDesde(@Param("tipo") String tipo, @Param("id") String id, @Param("desde") LocalDateTime desde);

    /**
     * La comisión que de verdad se retuvo, leída de las líneas de los negocios: [estado, modo, 'ID' si tiene id de Wompi,
     * suma]. Así un rechazo del banco o una anulación la mueven solos, y no depende de la línea PLATAFORMA, cuyo registro
     * no detiene un pago.
     */
    @Query("select r.estado, coalesce(r.modoLiquidacion, ''), "
            + "case when r.referenciaLiquidacion like '%[ID:%' then 'ID' else '' end, coalesce(sum(r.comisionRetenida), 0) "
            + "from RepartoPago r where r.beneficiarioTipo <> 'PLATAFORMA' and r.comisionRetenida > 0 "
            + "group by r.estado, coalesce(r.modoLiquidacion, ''), case when r.referenciaLiquidacion like '%[ID:%' then 'ID' else '' end")
    List<Object[]> comisionRetenidaPorEstado();

    /** Líneas con comisión cobrada desde una fecha: pagadas, sin simular y, las de Wompi, con el id de su pago. */
    @Query("select r from RepartoPago r where r.beneficiarioTipo <> 'PLATAFORMA' and r.comisionRetenida > 0 "
            + "and r.estado = 'LIQUIDADO' and coalesce(r.modoLiquidacion, '') <> 'SIMULADO' "
            + "and (coalesce(r.modoLiquidacion, '') <> 'WOMPI' or r.referenciaLiquidacion like '%[ID:%') "
            + "and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde")
    List<RepartoPago> comisionCobradaDesde(@Param("desde") LocalDateTime desde);

    /** Líneas de comisión de la plataforma (retenidas, informativas y sus anulaciones) creadas en [desde, antesDe). */
    @Query("select r from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA' and r.fechaCreacion >= :desde "
            + "and r.fechaCreacion < :antesDe")
    List<RepartoPago> comisionEntre(@Param("desde") LocalDateTime desde, @Param("antesDe") LocalDateTime antesDe);

    /** Como comisionCobradaDesde, pero solo las líneas pagadas en [desde, antesDe). */
    @Query("select r from RepartoPago r where r.beneficiarioTipo <> 'PLATAFORMA' and r.comisionRetenida > 0 "
            + "and r.estado = 'LIQUIDADO' and coalesce(r.modoLiquidacion, '') <> 'SIMULADO' "
            + "and (coalesce(r.modoLiquidacion, '') <> 'WOMPI' or r.referenciaLiquidacion like '%[ID:%') "
            + "and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde and coalesce(r.fechaLiquidacion, r.fechaCreacion) < :antesDe")
    List<RepartoPago> comisionCobradaEntre(@Param("desde") LocalDateTime desde, @Param("antesDe") LocalDateTime antesDe);

    /** Líneas de comisión (registradas y anuladas) desde una fecha, para el reporte por mes. */
    @Query("select r from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA' and r.fechaCreacion >= :desde "
            + "order by r.fechaCreacion")
    List<RepartoPago> comisionDesde(@Param("desde") LocalDateTime desde);

    /** Comisión solo registrada (líneas informativas de la plataforma, ya sin lo anulado por reembolsos). La retenida va aparte. */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA' and r.estado = 'INFORMATIVA'")
    double sumaComisionRegistrada();

    /** Lo que falta pagar a talleres y tiendas: líneas habilitadas (o con fallo) y su monto. */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.estado in ('POR_LIQUIDAR', 'FALLIDO')")
    double sumaPorLiquidar();

    /** Ya pagado a talleres y tiendas; con modo = SIMULADO, solo lo que liquidó la dispersión simulada. */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.estado = 'LIQUIDADO' and r.beneficiarioTipo <> 'PLATAFORMA'")
    double sumaLiquidada();

    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.estado = 'LIQUIDADO' and r.beneficiarioTipo <> 'PLATAFORMA' "
            + "and r.modoLiquidacion = 'SIMULADO'")
    double sumaLiquidadaSimulada();

    /** Lo que espera la entrega o la recepción del pedido para habilitarse (aún no se puede liquidar). */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.estado = 'PENDIENTE' and r.beneficiarioTipo <> 'PLATAFORMA'")
    double sumaEnEspera();

    /** Lo que falta pagar por estado, sin filtros: [estado, líneas, suma]. */
    @Query("select r.estado, count(r), coalesce(sum(r.monto), 0) from RepartoPago r where r.beneficiarioTipo <> 'PLATAFORMA' "
            + "and r.estado in ('PENDIENTE', 'POR_LIQUIDAR', 'FALLIDO', 'PROCESANDO') group by r.estado")
    List<Object[]> resumenSinPagar();

    /** Lo pagado en un periodo por modo: [modo, líneas, suma]. Las antiguas sin fecha de pago cuentan por la de creación. */
    @Query("select coalesce(r.modoLiquidacion, ''), count(r), coalesce(sum(r.monto), 0) from RepartoPago r "
            + "where r.estado = 'LIQUIDADO' and r.beneficiarioTipo <> 'PLATAFORMA' "
            + "and coalesce(r.fechaLiquidacion, r.fechaCreacion) >= :desde and coalesce(r.fechaLiquidacion, r.fechaCreacion) < :hasta "
            + "group by coalesce(r.modoLiquidacion, '')")
    List<Object[]> resumenPagado(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Desde cuándo se registra la comisión (los pagos anteriores no tienen línea de comisión). */
    @Query("select min(r.fechaCreacion) from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA'")
    java.time.LocalDateTime primeraComision();
    List<RepartoPago> findByOrdenServicioIdAndBeneficiarioTipo(String ordenServicioId, String beneficiarioTipo);
    List<RepartoPago> findByOrdenServicioIdInAndBeneficiarioTipo(java.util.Collection<String> ordenServicioIds, String beneficiarioTipo);

    /**
     * El evento ocurrió (recepción confirmada / orden ENTREGADA): PENDIENTE → POR_LIQUIDAR.
     * Solo líneas con la regla por evento; las anteriores a la Fase 4-B no se tocan.
     * Devuelve 1 si la marcó, 0 si ya no estaba pendiente.
     */
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'POR_LIQUIDAR', r.fechaListo = :ahora, r.eventoListo = :evento "
            + "where r.id = :id and r.estado = 'PENDIENTE' and r.liquidacionPorEvento = true")
    int marcarPorLiquidar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("evento") String evento);

    /**
     * Toma la línea para pagarla (→ PROCESANDO) en una sola sentencia: dos clics, o el
     * evento y el ADMIN a la vez, no la pagan dos veces. Solo se puede tomar una POR_LIQUIDAR
     * o una FALLIDO (reintento): una PENDIENTE espera su recepción o entrega, sea cual sea su
     * antigüedad. Nunca una con reembolso pendiente del mismo pedido o de todo el pago.
     * Devuelve 1 si la tomó, 0 si no.
     */
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'PROCESANDO', r.motivoFallo = null, r.fechaProcesando = :ahora, "
            + "r.intentosLiquidacion = coalesce(r.intentosLiquidacion, 0) + 1 "
            + "where r.id = :id and r.estado in ('POR_LIQUIDAR', 'FALLIDO') "
            + "and not exists (select 1 from Reembolso b where b.pagoId = r.pagoId and b.estado = 'PENDIENTE' "
            + "and (b.pedidoTiendaId is null or b.pedidoTiendaId = r.pedidoTiendaId))")
    int tomarParaLiquidar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'LIQUIDADO', r.fechaLiquidacion = :ahora, r.modoLiquidacion = :modo, "
            + "r.referenciaLiquidacion = :referencia, r.liquidadoPor = :por, r.nota = :nota, r.motivoFallo = null "
            + "where r.id = :id and r.estado = 'PROCESANDO'")
    int marcarLiquidado(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("modo") String modo,
                        @Param("referencia") String referencia, @Param("por") String por, @Param("nota") String nota);

    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'FALLIDO', r.motivoFallo = :motivo, r.modoLiquidacion = :modo "
            + "where r.id = :id and r.estado = 'PROCESANDO'")
    int marcarFallido(@Param("id") Long id, @Param("motivo") String motivo, @Param("modo") String modo);

    /**
     * La línea ya no se debe pagar (ej. la solicitud se rechazó sin taller): PENDIENTE → ANULADO.
     * Solo si sigue PENDIENTE; una ya habilitada o liquidada no se toca. Devuelve 1 si la anuló.
     */
    // Limpia la sesión de JPA: con open-in-view, una línea leída antes en la misma petición seguiría PENDIENTE y la
    // comisión de un reembolso posterior la contaría viva (pasó al cancelar una orden: no se anuló su comisión)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update RepartoPago r set r.estado = 'ANULADO', r.nota = :nota "
            + "where r.id = :id and r.estado = 'PENDIENTE'")
    int anularPendiente(@Param("id") Long id, @Param("nota") String nota);

    /** Wompi confirmó el pago de una línea que el ADMIN había dado por no salida: el dinero sí salió. */
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'LIQUIDADO', r.fechaLiquidacion = :ahora, r.liquidadoPor = :por, "
            + "r.nota = :nota, r.motivoFallo = null where r.id = :id and r.estado = 'FALLIDO'")
    int confirmarPagoTardio(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("por") String por,
                            @Param("nota") String nota);

    /** Wompi dio el id de un pago ya liquidado que no lo tenía. */
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.referenciaLiquidacion = :referencia where r.id = :id and r.estado = 'LIQUIDADO'")
    int anotarReferencia(@Param("id") Long id, @Param("referencia") String referencia);

    /** Wompi no confirmó el pago: la línea sigue PROCESANDO, con la referencia para reconocer su evento. */
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.motivoFallo = :motivo, r.referenciaLiquidacion = :referencia, r.modoLiquidacion = :modo "
            + "where r.id = :id and r.estado = 'PROCESANDO'")
    int marcarPorVerificar(@Param("id") Long id, @Param("referencia") String referencia,
                           @Param("motivo") String motivo, @Param("modo") String modo);

    // La referencia guardada es "WOMPI-<id>-<azar>" y, si Wompi dio su id, termina en " [ID:<payoutId>]"
    java.util.Optional<RepartoPago> findFirstByReferenciaLiquidacionStartingWith(String referencia);
    java.util.Optional<RepartoPago> findFirstByReferenciaLiquidacionEndingWith(String sufijo);

    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'FALLIDO', r.motivoFallo = :motivo "
            + "where r.id = :id and r.estado in ('PROCESANDO', 'LIQUIDADO')")
    int marcarFallidoAsincrono(@Param("id") Long id, @Param("motivo") String motivo);
}
