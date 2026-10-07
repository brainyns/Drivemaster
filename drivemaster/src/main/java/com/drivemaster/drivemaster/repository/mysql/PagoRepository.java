package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<PagoEntity, Long> {
    Optional<PagoEntity> findByReferencia(String referencia);

    /**
     * Toma un pago PENDIENTE para procesarlo (PENDIENTE → PROCESANDO) en una sola
     * sentencia: si el webhook y la redirección llegan a la vez, solo uno gana.
     * Devuelve 1 si lo tomó, 0 si ya estaba tomado o procesado.
     */
    @Modifying
    @Transactional
    @Query("update PagoEntity p set p.estado = 'PROCESANDO', p.transactionId = :txn "
            + "where p.id = :id and p.estado = 'PENDIENTE'")
    int reclamar(@Param("id") Long id, @Param("txn") String transactionId);

    /** Marca como RECHAZADO un pago aún PENDIENTE (Wompi lo declinó). */
    @Modifying
    @Transactional
    @Query("update PagoEntity p set p.estado = 'RECHAZADO', p.transactionId = :txn "
            + "where p.id = :id and p.estado = 'PENDIENTE'")
    int marcarRechazado(@Param("id") Long id, @Param("txn") String transactionId);
}
