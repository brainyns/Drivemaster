package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.CambioCorreo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CambioCorreoRepository extends MongoRepository<CambioCorreo, String> {
    Optional<CambioCorreo> findFirstByUsuarioIdOrderByCreadoDesc(String usuarioId);
    void deleteByUsuarioId(String usuarioId);
}
