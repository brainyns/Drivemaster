package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Desglose;
import com.drivemaster.drivemaster.service.DesgloseLiquidacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Venta, comisión y lo que recibe el negocio. El dueño sale de la sesión, nunca de un parámetro. */
@RestController
public class DesgloseLiquidacionController {

    private final DesgloseLiquidacionService desglose;

    public DesgloseLiquidacionController(DesgloseLiquidacionService desglose) {
        this.desglose = desglose;
    }

    @GetMapping("/api/pedidos-tienda/{id}/liquidacion")
    public ResponseEntity<Desglose> dePedido(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(desglose.dePedido(auth.getName(), id));
    }

    /** Diagnóstico y cotización de una orden del taller. */
    @GetMapping("/api/talleres/mi-taller/ordenes/{ordenId}/liquidacion")
    public ResponseEntity<Desglose> deOrden(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(desglose.deOrden(auth.getName(), ordenId));
    }
}
