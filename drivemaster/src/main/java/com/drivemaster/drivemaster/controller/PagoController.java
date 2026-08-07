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
        String redirectUrl = body.get("redirectUrl");
        return ResponseEntity.ok(pagoService.crearPago(solicitudId, redirectUrl));
    }

    @GetMapping("/verificar/{transactionId}")
    public ResponseEntity<Map<String, Object>> verificar(@PathVariable String transactionId) {
        return ResponseEntity.ok(pagoService.verificarTransaccion(transactionId));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> body) {
        pagoService.procesarWebhook(body);
        return ResponseEntity.ok().build();
    }
}
