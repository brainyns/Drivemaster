package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.OrdenServicio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
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

    // ─── Calificación de los talleres (CalificacionTallerService) ───

    List<OrdenServicio> findByTallerIdAndCalificacionTallerNotNull(String tallerId);

    List<OrdenServicio> findByCalificacionTallerNotNull();

    boolean existsByFlujoAndVehiculoIdAndEstadoIn(String flujo, String vehiculoId, Collection<String> estados);

    /** Citas de revisión aceptadas de un taller con fecha en [desde, hasta), de la más temprana a la más tarde. */
    @Query(value = "{ 'flujo': ?0, 'tallerId': ?1, 'aceptadaPorTaller': true, 'estado': { $nin: ?4 }, 'fechaProgramada': { $gte: ?2, $lt: ?3 } }",
            sort = "{ 'fechaProgramada': 1 }")
    List<OrdenServicio> citasDelTaller(String flujo, String tallerId, LocalDateTime desde, LocalDateTime hasta,
                                       Collection<String> estadosExcluidos);
}