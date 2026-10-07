package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.OrdenServicio;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface OrdenServicioRepository extends MongoRepository<OrdenServicio, String> {

    List<OrdenServicio> findByUsuarioIdOrderByFechaCreacionDesc(String usuarioId);

    List<OrdenServicio> findByTallerIdOrderByFechaCreacionDesc(String tallerId);

    List<OrdenServicio> findByVehiculoIdAndEstado(String vehiculoId, String estado);

    List<OrdenServicio> findByVehiculoIdAndEstadoOrderByFechaFinalizacionDesc(String vehiculoId, String estado);

    // ─── Marketplace (flujo = MARKETPLACE) ───

    List<OrdenServicio> findByFlujoAndUsuarioIdOrderByFechaCreacionDesc(String flujo, String usuarioId);

    List<OrdenServicio> findByFlujoAndTallerIdOrderByFechaCreacionDesc(String flujo, String tallerId);

    List<OrdenServicio> findByFlujoOrderByFechaCreacionDesc(String flujo);

    List<OrdenServicio> findByFlujoAndEstadoOrderByFechaCreacionDesc(String flujo, String estado);

    boolean existsByFlujoAndVehiculoIdAndEstadoIn(String flujo, String vehiculoId, Collection<String> estados);
}