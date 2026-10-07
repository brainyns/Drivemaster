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
}
