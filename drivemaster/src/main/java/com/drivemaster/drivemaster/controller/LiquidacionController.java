package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.service.LiquidacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Liquidaciones a talleres/tiendas y reembolsos a clientes (solo ADMIN). */
@RestController
@RequestMapping("/api/liquidaciones")
public class LiquidacionController {

    private final LiquidacionService service;

    public LiquidacionController(LiquidacionService service) {
        this.service = service;
    }

    @GetMapping("/repartos")
    public ResponseEntity<List<RepartoPago>> repartos(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.repartos(estado));
    }

    @PatchMapping("/repartos/{id}/liquidar")
    public ResponseEntity<RepartoPago> liquidar(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body,
                                                Authentication auth) {
        return ResponseEntity.ok(service.liquidar(id, auth.getName(), body != null ? body.get("nota") : null));
    }

    @GetMapping("/reembolsos")
    public ResponseEntity<List<Reembolso>> reembolsos(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.reembolsos(estado));
    }

    @PatchMapping("/reembolsos/{id}/realizado")
    public ResponseEntity<Reembolso> realizado(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, String> body,
                                               Authentication auth) {
        return ResponseEntity.ok(service.marcarReembolsado(id, auth.getName(), body != null ? body.get("nota") : null));
    }
}
