package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.mysql.PlanSuscripcion;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.service.SuscripcionService;
import com.drivemaster.drivemaster.service.TallerService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Planes y suscripciones de talleres y tiendas (solo ADMIN/SUPERADMIN). Apagado mientras DriveMaster cobre solo la
 * comisión: sin suscripciones.habilitadas=true estas rutas no existen (el código y las tablas se conservan).
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "suscripciones.habilitadas", havingValue = "true")
@RequestMapping("/api/suscripciones")
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final TallerService tallerService;
    private final TiendaService tiendaService;

    public SuscripcionController(SuscripcionService suscripcionService,
                                 TallerService tallerService,
                                 TiendaService tiendaService) {
        this.suscripcionService = suscripcionService;
        this.tallerService = tallerService;
        this.tiendaService = tiendaService;
    }

    @GetMapping("/planes")
    public ResponseEntity<List<PlanSuscripcion>> listarPlanes() {
        return ResponseEntity.ok(suscripcionService.listarPlanes());
    }

    @PutMapping("/planes/{codigo}")
    public ResponseEntity<PlanSuscripcion> actualizarPlan(@PathVariable String codigo, @RequestBody PlanSuscripcion datos) {
        return ResponseEntity.ok(suscripcionService.actualizarPlan(codigo, datos));
    }

    @GetMapping
    public ResponseEntity<List<Suscripcion>> historial(@RequestParam String actorTipo, @RequestParam String actorId) {
        return ResponseEntity.ok(suscripcionService.historial(actorTipo.toUpperCase(), actorId));
    }

    /** Body: { actorTipo: TALLER|TIENDA, actorId, planCodigo } */
    @PostMapping
    public ResponseEntity<Suscripcion> activar(@RequestBody Map<String, String> body, Authentication auth) {
        String actorTipo = body.get("actorTipo") != null ? body.get("actorTipo").trim().toUpperCase() : "";
        String actorId = body.get("actorId");
        if (actorId == null || actorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actorId es obligatorio");
        }
        // Valida que el negocio exista (404 si no).
        if (SuscripcionService.ACTOR_TALLER.equals(actorTipo)) tallerService.obtenerEntidad(actorId);
        else if (SuscripcionService.ACTOR_TIENDA.equals(actorTipo)) tiendaService.obtener(actorId);

        return ResponseEntity.ok(suscripcionService.activar(actorTipo, actorId, body.get("planCodigo"), auth.getName()));
    }
}
