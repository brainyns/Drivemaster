package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.service.TiendaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tiendas")
public class TiendaController {

    private final TiendaService tiendaService;

    public TiendaController(TiendaService tiendaService) {
        this.tiendaService = tiendaService;
    }

    // Registro público de una tienda; queda PENDIENTE de aprobación.
    @PostMapping("/registro")
    public ResponseEntity<TiendaDTO> registrar(@RequestBody RegistroNegocioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tiendaService.registrar(request));
    }

    // ─── Tienda autenticada ──────────────────────────────────

    @GetMapping("/mi-tienda")
    public ResponseEntity<TiendaDTO> miTienda(Authentication auth) {
        return ResponseEntity.ok(tiendaService.miTienda(auth.getName()));
    }

    @PutMapping("/mi-tienda")
    public ResponseEntity<TiendaDTO> actualizarMiTienda(@RequestBody RegistroNegocioRequest request, Authentication auth) {
        return ResponseEntity.ok(tiendaService.actualizarMiTienda(auth.getName(), request));
    }

    // ─── Administración (rol ADMIN/SUPERADMIN) ───────────────

    @GetMapping
    public ResponseEntity<List<TiendaDTO>> listar(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(tiendaService.listar(estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TiendaDTO> obtener(@PathVariable String id) {
        return ResponseEntity.ok(tiendaService.obtener(id));
    }

    @PatchMapping("/{id}/aprobacion")
    public ResponseEntity<TiendaDTO> resolverAprobacion(@PathVariable String id,
                                                        @RequestBody AprobacionRequest request,
                                                        Authentication auth) {
        return ResponseEntity.ok(tiendaService.resolverAprobacion(id, request, auth.getName()));
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<TiendaDTO> activarDesactivar(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        return ResponseEntity.ok(tiendaService.activarDesactivar(id, activo));
    }
}
