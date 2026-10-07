package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.mysql.PlanSuscripcion;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.repository.mysql.PlanSuscripcionRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Planes y suscripciones de talleres y tiendas. En v1 el ADMIN activa el plan a
 * mano; no hay cobro automático.
 */
@Service
public class SuscripcionService {

    public static final String ACTOR_TALLER = "TALLER";
    public static final String ACTOR_TIENDA = "TIENDA";
    private static final Set<String> ACTORES = Set.of(ACTOR_TALLER, ACTOR_TIENDA);

    private final PlanSuscripcionRepository planRepository;
    private final SuscripcionRepository suscripcionRepository;

    public SuscripcionService(PlanSuscripcionRepository planRepository,
                              SuscripcionRepository suscripcionRepository) {
        this.planRepository = planRepository;
        this.suscripcionRepository = suscripcionRepository;
    }

    public List<PlanSuscripcion> listarPlanes() {
        return planRepository.findAll();
    }

    /** El ADMIN ajusta nombre, descripción, precios y duración de un plan existente. */
    public PlanSuscripcion actualizarPlan(String codigo, PlanSuscripcion datos) {
        PlanSuscripcion plan = obtenerPlan(codigo);
        if (datos.getNombre() != null && !datos.getNombre().isBlank()) plan.setNombre(datos.getNombre());
        if (datos.getDescripcion() != null) plan.setDescripcion(datos.getDescripcion());
        if (datos.getPrecioMensualTaller() != null) plan.setPrecioMensualTaller(noNegativo(datos.getPrecioMensualTaller()));
        if (datos.getPrecioMensualTienda() != null) plan.setPrecioMensualTienda(noNegativo(datos.getPrecioMensualTienda()));
        plan.setDuracionDias(datos.getDuracionDias());
        if (datos.getActivo() != null) plan.setActivo(datos.getActivo());
        return planRepository.save(plan);
    }

    /**
     * Activa un plan para un negocio. La suscripción activa anterior (si existe)
     * queda CANCELADA, de modo que siempre hay como máximo una ACTIVA.
     */
    @Transactional
    public Suscripcion activar(String actorTipo, String actorId, String planCodigo, String activadaPor) {
        if (!ACTORES.contains(actorTipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de negocio inválido: " + actorTipo);
        }
        PlanSuscripcion plan = obtenerPlan(planCodigo != null && !planCodigo.isBlank()
                ? planCodigo.trim().toUpperCase() : PlanSuscripcion.PRUEBA);
        if (!Boolean.TRUE.equals(plan.getActivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El plan " + plan.getCodigo() + " no está disponible");
        }

        suscripcionRepository
                .findFirstByActorTipoAndActorIdAndEstadoOrderByFechaInicioDesc(actorTipo, actorId, Suscripcion.ACTIVA)
                .ifPresent(anterior -> {
                    anterior.setEstado(Suscripcion.CANCELADA);
                    suscripcionRepository.save(anterior);
                });

        LocalDateTime ahora = LocalDateTime.now();
        Suscripcion nueva = new Suscripcion();
        nueva.setActorTipo(actorTipo);
        nueva.setActorId(actorId);
        nueva.setPlanCodigo(plan.getCodigo());
        nueva.setEstado(Suscripcion.ACTIVA);
        nueva.setFechaInicio(ahora);
        nueva.setFechaFin(plan.getDuracionDias() != null ? ahora.plusDays(plan.getDuracionDias()) : null);
        nueva.setActivadaPor(activadaPor);
        return suscripcionRepository.save(nueva);
    }

    /** Suscripción activa y no vencida. Si encuentra una ya vencida, la marca VENCIDA. */
    public Optional<Suscripcion> vigente(String actorTipo, String actorId) {
        Optional<Suscripcion> activa = suscripcionRepository
                .findFirstByActorTipoAndActorIdAndEstadoOrderByFechaInicioDesc(actorTipo, actorId, Suscripcion.ACTIVA);
        if (activa.isPresent() && activa.get().getFechaFin() != null
                && activa.get().getFechaFin().isBefore(LocalDateTime.now())) {
            activa.get().setEstado(Suscripcion.VENCIDA);
            suscripcionRepository.save(activa.get());
            return Optional.empty();
        }
        return activa;
    }

    public List<Suscripcion> historial(String actorTipo, String actorId) {
        return suscripcionRepository.findByActorTipoAndActorIdOrderByFechaInicioDesc(actorTipo, actorId);
    }

    private PlanSuscripcion obtenerPlan(String codigo) {
        return planRepository.findById(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado: " + codigo));
    }

    private Double noNegativo(Double valor) {
        if (valor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio no puede ser negativo");
        }
        return valor;
    }
}
