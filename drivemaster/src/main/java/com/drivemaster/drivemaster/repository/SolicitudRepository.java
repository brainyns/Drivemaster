package com.drivemaster.drivemaster.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.Solicitud;

import java.util.List;

@Repository
public interface SolicitudRepository extends MongoRepository<Solicitud, String> {
    List<Solicitud> findByEstado(String estado);
    List<Solicitud> findByClienteId(String clienteId);
    List<Solicitud> findByUsuarioId(String usuarioId);
    List<Solicitud> findAllByOrderByFechaCreacionDesc();
}
