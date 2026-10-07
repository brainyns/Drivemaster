package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.Reembolso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {
    List<Reembolso> findAllByOrderByFechaCreacionDesc();
    List<Reembolso> findByEstadoOrderByFechaCreacionDesc(String estado);
    boolean existsByPagoId(Long pagoId);

    /** Hay un reembolso pendiente que afecta a esta línea: el de su pedido o el de todo el pago. */
    @Query("select count(b) > 0 from Reembolso b where b.pagoId = :pagoId "
            + "and b.estado = 'PENDIENTE' and (b.pedidoTiendaId is null or b.pedidoTiendaId = :pedidoTiendaId)")
    boolean hayPendienteQueAfecta(@Param("pagoId") Long pagoId, @Param("pedidoTiendaId") String pedidoTiendaId);
}
