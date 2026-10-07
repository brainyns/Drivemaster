package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.CompraCatalogo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CompraCatalogoRepository extends MongoRepository<CompraCatalogo, String> {
    List<CompraCatalogo> findByClienteIdOrderByFechaCreacionDesc(String clienteId);
    Optional<CompraCatalogo> findByReferenciaPago(String referenciaPago);
}
