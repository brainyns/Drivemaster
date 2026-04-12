package com.drivemaster.drivemaster.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.Compra;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CompraRepository extends MongoRepository<Compra, String> {

    List<Compra> findByFechaBetween( LocalDateTime inicio, LocalDateTime fin);

    List<Compra> findByProveedorId(String proveedorId);

    List<Compra> findByUsuarioId(String usuarioId);
}
