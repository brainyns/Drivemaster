package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.RepuestoBuscadoDTO;
import com.drivemaster.drivemaster.service.BuscadorRepuestosService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Buscador multi-tienda para el taller, filtrado por el vehículo de la orden. */
@RestController
@RequestMapping("/api/repuestos")
public class BuscadorRepuestosController {

    private final BuscadorRepuestosService buscador;

    public BuscadorRepuestosController(BuscadorRepuestosService buscador) {
        this.buscador = buscador;
    }

    /**
     * GET /api/repuestos/buscar?ordenId=...&q=pastillas&referencia=...&cantidad=2&soloCompatibles=true
     * Sin q ni referencia devuelve todo el inventario (compatible, si soloCompatibles).
     * Cada oferta trae su estado (COMPATIBLE, NO_COMPATIBLE, AGOTADO, POR_CONFIRMAR),
     * si se puede elegir y por qué no.
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<RepuestoBuscadoDTO>> buscar(@RequestParam String ordenId,
                                                           @RequestParam(required = false) String q,
                                                           @RequestParam(required = false) String referencia,
                                                           @RequestParam(required = false) Integer cantidad,
                                                           @RequestParam(defaultValue = "true") boolean soloCompatibles,
                                                           Authentication auth) {
        return ResponseEntity.ok(buscador.buscar(auth.getName(), ordenId, q, referencia, cantidad, soloCompatibles));
    }
}
