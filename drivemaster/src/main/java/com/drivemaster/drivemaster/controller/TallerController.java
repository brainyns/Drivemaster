package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.service.TallerService;
import org.springframework.http.ResponseEntity;
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

    // Administración de talleres (rol ADMIN/SUPERADMIN)
    @GetMapping
    public ResponseEntity<List<TallerDTO>> listarTodos() {
        return ResponseEntity.ok(tallerService.listarTodos());
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
}