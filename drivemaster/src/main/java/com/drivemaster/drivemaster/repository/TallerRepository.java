package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.Taller;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TallerRepository extends MongoRepository<Taller, String> {

    Optional<Taller> findByUsuarioId(String usuarioId);

    List<Taller> findByActivoTrue();

    List<Taller> findByCiudadIgnoreCaseAndActivoTrue(String ciudad);

    List<Taller> findByEstadoAprobacion(String estadoAprobacion);
}