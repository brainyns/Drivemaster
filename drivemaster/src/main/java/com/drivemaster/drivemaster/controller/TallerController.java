package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TallerMapaDTO;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.service.TallerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/talleres")
public class TallerController {

    private final TallerService tallerService;

    public TallerController(TallerService tallerService) {
        this.tallerService = tallerService;
    }

    // Listado para el cliente: talleres activos, opcionalmente filtrados por
    // ciudad y con distancia calculada (Haversine) si se envía lat/lng.
    @GetMapping("/activos")
    public ResponseEntity<List<TallerDTO>> listarActivos(
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(tallerService.listarActivos(ciudad, lat, lng));
    }

    // Mapa y lista de la solicitud de diagnóstico: talleres aprobados (los que no tienen ubicación, con lat/lng en null).
    @GetMapping("/mapa")
    public ResponseEntity<List<TallerMapaDTO>> mapa() {
        return ResponseEntity.ok(tallerService.listarParaMapa());
    }

    // Registro público de un taller; queda PENDIENTE de aprobación.
    @PostMapping("/registro")
    public ResponseEntity<TallerDTO> registrar(@RequestBody RegistroNegocioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tallerService.registrar(request));
    }

    // ─── Taller autenticado ──────────────────────────────────

    @GetMapping("/mi-taller")
    public ResponseEntity<TallerDTO> miTaller(Authentication auth) {
        return ResponseEntity.ok(tallerService.miTaller(auth.getName()));
    }

    @GetMapping("/mi-taller/mecanicos")
    public ResponseEntity<List<Mecanico>> listarMecanicos(Authentication auth) {
        return ResponseEntity.ok(tallerService.listarMecanicos(auth.getName()));
    }

    @PostMapping("/mi-taller/mecanicos")
    public ResponseEntity<Mecanico> agregarMecanico(@RequestBody Mecanico mecanico, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tallerService.agregarMecanico(auth.getName(), mecanico));
    }

    @PutMapping("/mi-taller/mecanicos/{mecanicoId}")
    public ResponseEntity<Mecanico> actualizarMecanico(@PathVariable String mecanicoId,
                                                       @RequestBody Mecanico mecanico,
                                                       Authentication auth) {
        return ResponseEntity.ok(tallerService.actualizarMecanico(auth.getName(), mecanicoId, mecanico));
    }

    @PatchMapping("/mi-taller/mecanicos/{mecanicoId}/activo")
    public ResponseEntity<Mecanico> cambiarEstadoMecanico(@PathVariable String mecanicoId,
                                                          @RequestBody Map<String, Boolean> body,
                                                          Authentication auth) {
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        return ResponseEntity.ok(tallerService.cambiarEstadoMecanico(auth.getName(), mecanicoId, activo));
    }

    // ─── Administración de talleres (rol ADMIN/SUPERADMIN) ───

    @GetMapping
    public ResponseEntity<List<?>> listarTodos(@RequestParam(required = false) String estado, Authentication auth) {
        List<TallerDTO> talleres = tallerService.listarTodos(estado);
        // El ADMIN ve la lista sin teléfono, correos ni coordenadas; el SUPERADMIN, completa
        return ResponseEntity.ok(com.drivemaster.drivemaster.security.PermisosAprobacion.esSuperadmin(auth)
                ? talleres
                : talleres.stream().map(com.drivemaster.drivemaster.dto.plataforma.RespuestasAdminDTO.TallerResumen::de).toList());
    }

    @PostMapping
    public ResponseEntity<TallerDTO> crear(@RequestBody CrearTallerRequest request) {
        return ResponseEntity.ok(tallerService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TallerDTO> actualizar(@PathVariable String id, @RequestBody CrearTallerRequest request) {
        return ResponseEntity.ok(tallerService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<TallerDTO> activarDesactivar(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        return ResponseEntity.ok(tallerService.activarDesactivar(id, activo));
    }

    @PatchMapping("/{id}/aprobacion")
    public ResponseEntity<Object> resolverAprobacion(@PathVariable String id,
                                                     @RequestBody AprobacionRequest request,
                                                     Authentication auth) {
        com.drivemaster.drivemaster.security.PermisosAprobacion.validarPlan(auth, request);
        TallerDTO resultado = tallerService.resolverAprobacion(id, request, auth.getName());
        return ResponseEntity.ok(com.drivemaster.drivemaster.security.PermisosAprobacion.esSuperadmin(auth)
                ? resultado : com.drivemaster.drivemaster.dto.plataforma.RespuestasAdminDTO.ResultadoAprobacion.de(resultado));
    }
}
