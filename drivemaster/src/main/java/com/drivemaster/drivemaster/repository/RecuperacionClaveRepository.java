package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.RecuperacionClave;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RecuperacionClaveRepository extends MongoRepository<RecuperacionClave, String> {
    Optional<RecuperacionClave> findFirstByCorreoOrderByCreadoDesc(String correo);
    void deleteByCorreo(String correo);
}
