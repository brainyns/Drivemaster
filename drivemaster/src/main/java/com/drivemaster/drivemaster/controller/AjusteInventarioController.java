package com.drivemaster.drivemaster.controller;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;

@RestController
@RequestMapping("/inventario")
public class AjusteInventarioController {

    private final MovimientoInventarioService movimientoService;
    private final AlcanceTienda alcanceTienda;

    public AjusteInventarioController(
            MovimientoInventarioService movimientoService,
            AlcanceTienda alcanceTienda) {

        this.movimientoService = movimientoService;
        this.alcanceTienda = alcanceTienda;

    }

    @PutMapping("/ajuste/{productoId}/{cantidad}")
    public ResponseEntity<?> ajustarStock(

            @PathVariable String productoId,
            @PathVariable int cantidad,
            @RequestParam String motivo,
            Authentication auth

    ) {

        // Una tienda solo ajusta su propio inventario.
        alcanceTienda.productoGestionable(auth, productoId);

        movimientoService.registrarMovimiento(
                productoId,
                "AJUSTE",
                cantidad,
                motivo,
                "AJUSTE-" + LocalDateTime.now(),
                auth.getName()

        );

        return ResponseEntity.ok("Stock ajustado correctamente");

    }

}
