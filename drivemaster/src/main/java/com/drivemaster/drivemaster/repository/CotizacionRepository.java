package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.Cotizacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CotizacionRepository extends MongoRepository<Cotizacion, String> {

    List<Cotizacion> findByOrdenIdOrderByVersionDesc(String ordenId);

    Optional<Cotizacion> findFirstByOrdenIdOrderByVersionDesc(String ordenId);

    List<Cotizacion> findByTallerIdOrderByFechaEmisionDesc(String tallerId);

    Optional<Cotizacion> findByTokenHash(String tokenHash);

    List<Cotizacion> findByEstadoAndVigenciaHastaBefore(String estado, LocalDateTime fecha);
}
