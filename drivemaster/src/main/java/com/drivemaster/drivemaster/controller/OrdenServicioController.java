package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.CrearOrdenServicioRequest;
import com.drivemaster.drivemaster.dto.FinalizarOrdenRequest;
import com.drivemaster.drivemaster.dto.OrdenServicioDTO;
import com.drivemaster.drivemaster.dto.PagarOrdenRequest;
import com.drivemaster.drivemaster.dto.ProgramarCitaRequest;
import com.drivemaster.drivemaster.service.OrdenServicioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordenes-servicio")
public class OrdenServicioController {

    private final OrdenServicioService ordenServicioService;

    public OrdenServicioController(OrdenServicioService ordenServicioService) {
        this.ordenServicioService = ordenServicioService;
    }

    // ─── CLIENTE ─────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<OrdenServicioDTO> cotizar(@RequestBody CrearOrdenServicioRequest request, Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.cotizar(auth.getName(), request));
    }

    // El cliente reporta el pago; la orden pasa a PAGADA cuando el taller lo confirma.
    @PostMapping("/{id}/pagar")
    public ResponseEntity<OrdenServicioDTO> pagar(@PathVariable String id,
                                                  @RequestBody(required = false) PagarOrdenRequest request,
                                                  Authentication auth) {
        String metodo = request != null ? request.getMetodo() : null;
        String referencia = request != null ? request.getReferencia() : null;
        return ResponseEntity.ok(ordenServicioService.reportarPago(auth.getName(), id, metodo, referencia));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<OrdenServicioDTO> cancelar(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.cancelar(auth.getName(), id));
    }

    @GetMapping("/mis-ordenes")
    public ResponseEntity<List<OrdenServicioDTO>> misOrdenes(Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.listarPorUsuario(auth.getName()));
    }

    // ─── TALLER ──────────────────────────────────────────────

    @GetMapping("/taller")
    public ResponseEntity<List<OrdenServicioDTO>> ordenesDelTaller(Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.listarPorTaller(auth.getName()));
    }

    @PatchMapping("/{id}/confirmar-pago")
    public ResponseEntity<OrdenServicioDTO> confirmarPago(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.confirmarPago(auth.getName(), id));
    }

    @PatchMapping("/{id}/iniciar")
    public ResponseEntity<OrdenServicioDTO> iniciar(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.iniciarServicio(auth.getName(), id));
    }

    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<OrdenServicioDTO> finalizar(@PathVariable String id,
                                                        @RequestBody(required = false) FinalizarOrdenRequest request,
                                                        Authentication auth) {
        String observaciones = request != null ? request.getObservaciones() : null;
        return ResponseEntity.ok(ordenServicioService.finalizarServicio(auth.getName(), id, observaciones));
    }
    
    @PatchMapping("/{id}/programar")
    public ResponseEntity<OrdenServicioDTO> programar(@PathVariable String id,
                                                     @RequestBody ProgramarCitaRequest request,
                                                     Authentication auth) {
        return ResponseEntity.ok(
                ordenServicioService.programarCita(auth.getName(), id, request.getFechaProgramada()));
    }

    @GetMapping("/taller/{tallerId}")
public ResponseEntity<List<OrdenServicioDTO>> ordenesPorTallerAdmin(@PathVariable String tallerId) {
    return ResponseEntity.ok(ordenServicioService.listarPorTallerAdmin(tallerId));
}
}