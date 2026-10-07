package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.PagoMarketplaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Links de pago del marketplace (requieren sesión de CLIENTE). La confirmación
 * entra por /api/pagos (webhook, redirección o verificar), siempre verificada
 * contra la API de Wompi.
 */
@RestController
@RequestMapping("/api/pagos-marketplace")
public class PagoMarketplaceController {

    private final PagoMarketplaceService service;

    public PagoMarketplaceController(PagoMarketplaceService service) {
        this.service = service;
    }

    @PostMapping("/diagnostico/{ordenId}")
    public ResponseEntity<Map<String, Object>> pagarDiagnostico(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(service.iniciarPagoDiagnostico(auth.getName(), ordenId));
    }

    @PostMapping("/cotizacion/{ordenId}")
    public ResponseEntity<Map<String, Object>> pagarCotizacion(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(service.iniciarPagoCotizacion(auth.getName(), ordenId));
    }
}
