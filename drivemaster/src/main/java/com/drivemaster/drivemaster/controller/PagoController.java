package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.PagoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
@CrossOrigin(origins = "http://localhost:5173")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/crear")
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, String> body) {
        String solicitudId = body.get("solicitudId");
        String ventaId = body.get("ventaId");
        String redirectUrl = body.get("redirectUrl");
        if (ventaId != null && !ventaId.isBlank()) {
            return ResponseEntity.ok(pagoService.crearPagoVenta(ventaId, redirectUrl));
        }
        return ResponseEntity.ok(pagoService.crearPago(solicitudId, redirectUrl));
    }

    @GetMapping("/verificar/{transactionId}")
    public ResponseEntity<Map<String, Object>> verificar(@PathVariable String transactionId) {
        return ResponseEntity.ok(pagoService.verificarTransaccion(transactionId));
    }

    @GetMapping("/confirmar-redirect")
    public ResponseEntity<Void> confirmarRedirect(@RequestParam(value = "id", required = false) String transactionId) {
        if (transactionId != null && !transactionId.isBlank()) {
            pagoService.verificarTransaccion(transactionId);
        }
        String destino = "http://localhost:5173/pago-resultado" + (transactionId != null ? "?id=" + transactionId : "");
        return ResponseEntity.status(302).location(java.net.URI.create(destino)).build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> body) {
        pagoService.procesarWebhook(body);
        return ResponseEntity.ok().build();
    }
}
