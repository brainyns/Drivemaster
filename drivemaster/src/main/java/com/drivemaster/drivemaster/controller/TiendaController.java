package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.CuentaBancariaDTO;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO;
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

    @PutMapping("/mi-tienda/cuenta-bancaria")
    public ResponseEntity<TiendaDTO> guardarCuentaBancaria(@RequestBody CuentaBancariaDTO dto, Authentication auth) {
        return ResponseEntity.ok(tiendaService.guardarCuentaBancaria(auth.getName(), dto));
    }

    @PutMapping("/mi-tienda/telefono")
    public ResponseEntity<TiendaDTO> telefono(@RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(tiendaService.actualizarTelefono(auth.getName(), body == null ? null : body.get("telefono")));
    }

    /** Horario por días (el mismo formato que /api/talleres/{id}/horario): reemplaza el de la semana completa. */
    @PutMapping("/mi-tienda/horario")
    public ResponseEntity<TiendaDTO> horario(@RequestBody List<FranjaHorarioDTO> franjas, Authentication auth) {
        return ResponseEntity.ok(tiendaService.guardarHorario(auth.getName(), franjas));
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
    public ResponseEntity<Object> resolverAprobacion(@PathVariable String id,
                                                     @RequestBody AprobacionRequest request,
                                                     Authentication auth) {
        com.drivemaster.drivemaster.security.PermisosAprobacion.validarPlan(auth, request);
        TiendaDTO resultado = tiendaService.resolverAprobacion(id, request, auth.getName());
        // El ADMIN recibe solo lo mínimo (sin teléfono, correos ni coordenadas); el SUPERADMIN, el DTO completo
        return ResponseEntity.ok(com.drivemaster.drivemaster.security.PermisosAprobacion.esSuperadmin(auth)
                ? resultado : com.drivemaster.drivemaster.dto.plataforma.RespuestasAdminDTO.ResultadoAprobacion.de(resultado));
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<TiendaDTO> activarDesactivar(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        return ResponseEntity.ok(tiendaService.activarDesactivar(id, activo));
    }
}
