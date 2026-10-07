package com.drivemaster.drivemaster.repository.mysql;

import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findFirstByActorTipoAndActorIdAndEstadoOrderByFechaInicioDesc(
            String actorTipo, String actorId, String estado);

    List<Suscripcion> findByActorTipoAndActorIdOrderByFechaInicioDesc(String actorTipo, String actorId);

    List<Suscripcion> findByEstado(String estado);
}
