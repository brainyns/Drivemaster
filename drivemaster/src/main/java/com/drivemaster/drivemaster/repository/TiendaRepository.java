package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.Tienda;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TiendaRepository extends MongoRepository<Tienda, String> {

    Optional<Tienda> findByUsuarioId(String usuarioId);

    List<Tienda> findByEstadoAprobacion(String estadoAprobacion);
}
