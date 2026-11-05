package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.ResolucionReparto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolucionRepartoRepository extends JpaRepository<ResolucionReparto, Long> {
    boolean existsByRepartoIdAndResultado(Long repartoId, String resultado);
    boolean existsByResultadoAndReferenciaStartingWith(String resultado, String referencia);
    boolean existsByResultadoAndReferenciaEndingWith(String resultado, String sufijo);
}
