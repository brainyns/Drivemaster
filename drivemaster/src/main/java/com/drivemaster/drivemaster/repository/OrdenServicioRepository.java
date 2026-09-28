package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.OrdenServicio;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrdenServicioRepository extends MongoRepository<OrdenServicio, String> {

    List<OrdenServicio> findByUsuarioIdOrderByFechaCreacionDesc(String usuarioId);

    List<OrdenServicio> findByTallerIdOrderByFechaCreacionDesc(String tallerId);

    List<OrdenServicio> findByVehiculoIdAndEstado(String vehiculoId, String estado);

    List<OrdenServicio> findByVehiculoIdAndEstadoOrderByFechaFinalizacionDesc(String vehiculoId, String estado);
}