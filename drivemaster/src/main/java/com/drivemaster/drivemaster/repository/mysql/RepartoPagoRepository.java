package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepartoPagoRepository extends JpaRepository<RepartoPago, Long> {
    List<RepartoPago> findAllByOrderByFechaCreacionDesc();
    List<RepartoPago> findByEstadoOrderByFechaCreacionDesc(String estado);
    List<RepartoPago> findByPedidoTiendaId(String pedidoTiendaId);
}
