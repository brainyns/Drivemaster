package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.Notificacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findByUsuarioIdOrderByFechaCreacionDesc(String usuarioId, Pageable pageable);
    long countByUsuarioIdAndLeidaFalse(String usuarioId);
}
