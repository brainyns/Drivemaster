package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface RepartoPagoRepository extends JpaRepository<RepartoPago, Long> {
    List<RepartoPago> findAllByOrderByFechaCreacionDesc();
    List<RepartoPago> findByEstadoOrderByFechaCreacionDesc(String estado);
    List<RepartoPago> findByPedidoTiendaId(String pedidoTiendaId);
    boolean existsByPagoIdAndBeneficiarioTipo(Long pagoId, String beneficiarioTipo);
    long countByEstado(String estado);

    boolean existsByPagoIdAndBeneficiarioTipoAndReferenciaLiquidacion(Long pagoId, String beneficiarioTipo, String referencia);

    /** Comisión vigente de un pago: la registrada menos lo anulado por reembolsos. */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.pagoId = :pagoId and r.beneficiarioTipo = 'PLATAFORMA'")
    double sumaComisionDePago(@Param("pagoId") Long pagoId);

    /** Comisión originalmente registrada de un pago (sin descontar anulaciones). */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.pagoId = :pagoId and r.beneficiarioTipo = 'PLATAFORMA' and r.monto > 0")
    double sumaComisionBruta(@Param("pagoId") Long pagoId);

    /** Lo repartido a cada taller (sin lo anulado): [beneficiarioId, suma]. */
    @Query("select r.beneficiarioId, sum(r.monto) from RepartoPago r "
            + "where r.beneficiarioTipo = 'TALLER' and r.estado <> 'ANULADO' group by r.beneficiarioId")
    List<Object[]> sumaRepartidaPorTaller();

    /** Resumen de lo repartido a un beneficiario por estado: [estado, líneas, suma]. */
    @Query("select r.estado, count(r), sum(r.monto) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id group by r.estado")
    List<Object[]> resumenPorEstado(@Param("tipo") String tipo, @Param("id") String id);

    /** Lo repartido a un beneficiario por concepto y estado: [concepto, estado, líneas, suma]. */
    @Query("select r.concepto, r.estado, count(r), sum(r.monto) from RepartoPago r "
            + "where r.beneficiarioTipo = :tipo and r.beneficiarioId = :id group by r.concepto, r.estado")
    List<Object[]> resumenPorConceptoYEstado(@Param("tipo") String tipo, @Param("id") String id);

    /**
     * Lo repartido a un beneficiario en un periodo, por concepto y estado: [concepto, estado, líneas, suma]. Cada línea
     * cuenta una sola vez, en su estado actual: la liquidada por la fecha en que se pagó (las antiguas sin esa fecha, por
     * la de creación) y las demás por la fecha en que se creó (cuando el cliente pagó).
     */
    @Query("select r.concepto, r.estado, count(r), sum(r.monto) from RepartoPago r "
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

    /** Líneas de comisión (registradas y anuladas) desde una fecha, para el reporte por mes. */
    @Query("select r from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA' and r.fechaCreacion >= :desde "
            + "order by r.fechaCreacion")
    List<RepartoPago> comisionDesde(@Param("desde") LocalDateTime desde);

    /** Comisión registrada (líneas informativas de la plataforma, ya sin lo anulado por reembolsos). */
    @Query("select coalesce(sum(r.monto), 0) from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA'")
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

    /** Desde cuándo se registra la comisión (los pagos anteriores no tienen línea de comisión). */
    @Query("select min(r.fechaCreacion) from RepartoPago r where r.beneficiarioTipo = 'PLATAFORMA'")
    java.time.LocalDateTime primeraComision();
    List<RepartoPago> findByOrdenServicioIdAndBeneficiarioTipo(String ordenServicioId, String beneficiarioTipo);

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
     * evento y el ADMIN a la vez, no la pagan dos veces. Se puede tomar una POR_LIQUIDAR,
     * una FALLIDO (reintento) o una PENDIENTE anterior a la regla por evento. Nunca una
     * con reembolso pendiente del mismo pedido o de todo el pago.
     * Devuelve 1 si la tomó, 0 si no.
     */
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'PROCESANDO', r.motivoFallo = null, "
            + "r.intentosLiquidacion = coalesce(r.intentosLiquidacion, 0) + 1 "
            + "where r.id = :id and (r.estado in ('POR_LIQUIDAR', 'FALLIDO') "
            + "or (r.estado = 'PENDIENTE' and (r.liquidacionPorEvento is null or r.liquidacionPorEvento = false))) "
            + "and not exists (select 1 from Reembolso b where b.pagoId = r.pagoId and b.estado = 'PENDIENTE' "
            + "and (b.pedidoTiendaId is null or b.pedidoTiendaId = r.pedidoTiendaId))")
    int tomarParaLiquidar(@Param("id") Long id);

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
    @Modifying
    @Transactional
    @Query("update RepartoPago r set r.estado = 'ANULADO', r.nota = :nota "
            + "where r.id = :id and r.estado = 'PENDIENTE'")
    int anularPendiente(@Param("id") Long id, @Param("nota") String nota);
}
