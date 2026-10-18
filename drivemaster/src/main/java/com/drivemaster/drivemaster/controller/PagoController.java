package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.PagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    private final PagoService pagoService;

    @Value("${frontend.url}")
    private String frontendUrl;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/crear")
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, String> body, Authentication auth) {
        String solicitudId = body.get("solicitudId");
        String ventaId = body.get("ventaId");
        String redirectUrl = body.get("redirectUrl");
        if (ventaId != null && !ventaId.isBlank()) {
            return ResponseEntity.ok(pagoService.crearPagoVenta(ventaId, redirectUrl, auth.getName()));
        }
        return ResponseEntity.ok(pagoService.crearPago(solicitudId, redirectUrl, auth.getName()));
    }

    @GetMapping("/verificar/{transactionId}")
    public ResponseEntity<Map<String, Object>> verificar(@PathVariable String transactionId) {
        return ResponseEntity.ok(pagoService.verificarTransaccion(transactionId));
    }

    // Wompi devuelve aquí al cliente. Pase lo que pase con la verificación, el cliente vuelve al frontend:
    // si falla, va con verificacion=error y la pantalla de resultado vuelve a verificar por su cuenta
    // (el pago se aplica igual por el webhook o por esa verificación).
    @GetMapping("/confirmar-redirect")
    public ResponseEntity<Void> confirmarRedirect(@RequestParam(value = "id", required = false) String transactionId) {
        boolean hayId = transactionId != null && !transactionId.isBlank();
        boolean fallo = false;
        if (hayId) {
            try {
                pagoService.verificarTransaccion(transactionId);
            } catch (Exception e) {
                fallo = true;
                log.error("confirmar-redirect: no se pudo verificar la transacción {}; se redirige igual al frontend",
                        transactionId, e);
            }
        }
        return ResponseEntity.status(302).location(destinoResultado(frontendUrl, hayId ? transactionId : null, fallo)).build();
    }

    /** FRONTEND_URL + "/?pago=resultado", con el id de Wompi (codificado) y la marca de error si la hubo. */
    static URI destinoResultado(String frontendUrl, String transactionId, boolean fallo) {
        String base = frontendUrl == null ? "" : frontendUrl.strip().replaceAll("/+$", "");
        StringBuilder destino = new StringBuilder(base).append("/?pago=resultado");
        if (transactionId != null) {
            destino.append("&id=").append(URLEncoder.encode(transactionId, StandardCharsets.UTF_8));
        }
        if (fallo) {
            destino.append("&verificacion=error");
        }
        return URI.create(destino.toString());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> body) {
        pagoService.procesarWebhook(body);
        return ResponseEntity.ok().build();
    }
}
