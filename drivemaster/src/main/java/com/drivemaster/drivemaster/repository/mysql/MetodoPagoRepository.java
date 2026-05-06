package com.drivemaster.drivemaster.repository.mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.mysql.MetodoPago;

@Repository
public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Integer> {

    List<MetodoPago> findByActivoTrue();

    Optional<MetodoPago> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}