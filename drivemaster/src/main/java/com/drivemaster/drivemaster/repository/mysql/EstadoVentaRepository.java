package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstadoVentaRepository extends JpaRepository<EstadoVenta, Integer> {

    List<EstadoVenta> findByActivoTrue();

    Optional<EstadoVenta> findByCodigo(String codigo);
}