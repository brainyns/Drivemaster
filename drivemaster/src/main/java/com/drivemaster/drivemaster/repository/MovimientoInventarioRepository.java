package com.drivemaster.drivemaster.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.MovimientoInventario;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends MongoRepository<MovimientoInventario, String> {

    List<MovimientoInventario> findByProductoId(String productoId);

    List<MovimientoInventario> findByProductoIdOrderByFechaDesc(String productoId);

    List<MovimientoInventario> findAllByOrderByFechaDesc();

    List<MovimientoInventario> findByTipo(String tipo);

    List<MovimientoInventario> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);

    List<MovimientoInventario> findByProductoIdAndTipo(String productoId, String tipo);

}
