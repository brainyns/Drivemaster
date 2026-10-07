package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.Reembolso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {
    List<Reembolso> findAllByOrderByFechaCreacionDesc();
    List<Reembolso> findByEstadoOrderByFechaCreacionDesc(String estado);
    boolean existsByPagoId(Long pagoId);
}
