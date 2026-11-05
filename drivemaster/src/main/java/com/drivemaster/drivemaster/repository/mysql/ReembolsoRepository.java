package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.Reembolso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {
    List<Reembolso> findAllByOrderByFechaCreacionDesc();
    List<Reembolso> findByEstadoOrderByFechaCreacionDesc(String estado);
    boolean existsByPagoId(Long pagoId);
    long countByEstado(String estado);
    List<Reembolso> findByPagoId(Long pagoId);
    List<Reembolso> findByOrdenServicioId(String ordenServicioId);

    /** Lo que un negocio debe a DriveMaster y aún no se le cobra. */
    @Query("select b from Reembolso b where b.saldoDeudorTipo = :tipo and b.saldoDeudorId = :id and b.saldoAFavor > 0 "
            + "and b.saldoCobradoEn is null order by b.fechaCreacion")
    List<Reembolso> saldosPendientesDe(@Param("tipo") String tipo, @Param("id") String id);

    @Query("select coalesce(sum(b.monto), 0) from Reembolso b where b.estado = 'PENDIENTE'")
    double sumaPendiente();

    /** Saldos a favor de DriveMaster que falta cobrarles a los negocios, el más antiguo primero. */
    @Query("select b from Reembolso b where b.saldoAFavor > 0 and b.saldoCobradoEn is null order by b.fechaCreacion")
    List<Reembolso> saldosAFavorPendientes();

    @Query("select coalesce(sum(b.saldoAFavor), 0) from Reembolso b where b.saldoAFavor > 0 and b.saldoCobradoEn is null")
    double sumaSaldosAFavorPendientes();

    /** Devuelve 1 si lo marcó, 0 si no hay saldo pendiente: dos clics no lo marcan dos veces. */
    @Modifying
    @Transactional
    @Query("update Reembolso b set b.saldoCobradoEn = :ahora, b.saldoCobradoPor = :por "
            + "where b.id = :id and b.saldoAFavor > 0 and b.saldoCobradoEn is null")
    int marcarSaldoCobrado(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("por") String por);

    /** Hay un reembolso pendiente que afecta a esta línea: el de su pedido o el de todo el pago. */
    @Query("select count(b) > 0 from Reembolso b where b.pagoId = :pagoId "
            + "and b.estado = 'PENDIENTE' and (b.pedidoTiendaId is null or b.pedidoTiendaId = :pedidoTiendaId)")
    boolean hayPendienteQueAfecta(@Param("pagoId") Long pagoId, @Param("pedidoTiendaId") String pedidoTiendaId);
}
