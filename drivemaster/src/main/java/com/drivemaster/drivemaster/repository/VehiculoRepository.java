package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.Vehiculo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VehiculoRepository extends MongoRepository<Vehiculo, String> {

    List<Vehiculo> findByUsuarioId(String usuarioId);
    boolean existsByPlaca(String placa);
}