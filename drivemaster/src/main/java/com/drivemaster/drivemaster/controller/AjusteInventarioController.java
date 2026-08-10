package com.drivemaster.drivemaster.controller;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.service.MovimientoInventarioService;

@RestController
@RequestMapping("/inventario")
public class AjusteInventarioController {

    private final MovimientoInventarioService movimientoService;

    public AjusteInventarioController(
            MovimientoInventarioService movimientoService) {

        this.movimientoService = movimientoService;

    }

    @PutMapping("/ajuste/{productoId}/{cantidad}")
    public ResponseEntity<?> ajustarStock(

            @PathVariable String productoId,
            @PathVariable int cantidad,
            @RequestParam String motivo

    ) {

        movimientoService.registrarMovimiento(
                productoId,
                "AJUSTE",
                cantidad,
                motivo,
                "AJUSTE-" + LocalDateTime.now(),
                "2"

        );

        return ResponseEntity.ok("Stock ajustado correctamente");

    }

}