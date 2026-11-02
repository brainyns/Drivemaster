package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO;
import com.drivemaster.drivemaster.service.InventarioTallerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Mi inventario". Solo el rol TALLER (SecurityConfig), y cada taller ve lo suyo: sale de la sesión. */
@RestController
@RequestMapping("/api/inventario-taller")
public class InventarioTallerController {

    private final InventarioTallerService inventario;

    public InventarioTallerController(InventarioTallerService inventario) {
        this.inventario = inventario;
    }

    @GetMapping
    public ResponseEntity<InventarioTallerDTO.Inventario> mio(Authentication auth) {
        return ResponseEntity.ok(inventario.inventario(auth.getName()));
    }

    @PatchMapping("/piezas/precio")
    public ResponseEntity<InventarioTallerDTO.Pieza> precio(@org.springframework.web.bind.annotation.RequestParam String piezaId,
                                                            @RequestBody InventarioTallerDTO.CambioPrecio cuerpo, Authentication auth) {
        return ResponseEntity.ok(inventario.cambiarPrecio(auth.getName(), piezaId, cuerpo != null ? cuerpo.precioVenta() : null));
    }

    @PostMapping("/pedidos/{pedidoId}/llego")
    public ResponseEntity<InventarioTallerDTO.Inventario> llego(@PathVariable String pedidoId, Authentication auth) {
        return ResponseEntity.ok(inventario.confirmarLlegada(auth.getName(), pedidoId));
    }
}
