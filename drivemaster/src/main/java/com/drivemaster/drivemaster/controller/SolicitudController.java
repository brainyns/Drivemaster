package com.drivemaster.drivemaster.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.dto.CrearSolicitudRequest;
import com.drivemaster.drivemaster.dto.SolicitudDTO;
import com.drivemaster.drivemaster.service.SolicitudService;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    private final SolicitudService solicitudService;

    public SolicitudController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @PostMapping
    public ResponseEntity<SolicitudDTO> crear(@RequestBody CrearSolicitudRequest request, Authentication auth) {
        return ResponseEntity.ok(
                solicitudService.crearSolicitud(auth.getName(), request.getProductos(), request.getMetodoPago()));
    }

    @GetMapping
    public ResponseEntity<List<SolicitudDTO>> listar() {
        return ResponseEntity.ok(solicitudService.listarTodas());
    }

    @GetMapping("/mis-solicitudes")
    public ResponseEntity<List<SolicitudDTO>> misSolicitudes(Authentication auth) {
        return ResponseEntity.ok(solicitudService.listarPorEmail(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitudDTO> obtener(@PathVariable String id) {
        return ResponseEntity.ok(solicitudService.obtenerPorId(id));
    }

    @PatchMapping("/{id}/aprobar")
    public ResponseEntity<SolicitudDTO> aprobar(@PathVariable String id) {
        return ResponseEntity.ok(solicitudService.aprobar(id));
    }

    @PatchMapping("/{id}/rechazar")
    public ResponseEntity<SolicitudDTO> rechazar(@PathVariable String id, @RequestBody Map<String, String> body) {
        String motivo = body.getOrDefault("motivo", "Solicitud rechazada");
        return ResponseEntity.ok(solicitudService.rechazar(id, motivo));
    }
}
