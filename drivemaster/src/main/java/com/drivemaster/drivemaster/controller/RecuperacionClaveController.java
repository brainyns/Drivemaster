package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.RecuperacionClaveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** "¿Olvidaste tu contraseña?" desde el login, sin sesión (ver RecuperacionClaveService). */
@RestController
@RequestMapping("/api/auth/recuperar")
public class RecuperacionClaveController {

    private final RecuperacionClaveService recuperacion;

    public RecuperacionClaveController(RecuperacionClaveService recuperacion) {
        this.recuperacion = recuperacion;
    }

    /** {"correo": "..."} → siempre el mismo mensaje, exista o no la cuenta. */
    @PostMapping("/solicitar")
    public ResponseEntity<Map<String, String>> solicitar(@RequestBody Map<String, String> body, HttpServletRequest http) {
        // getRemoteAddr ya es la IP del cliente detrás del proxy (ver ChatProxyController)
        return ResponseEntity.ok(Map.of("mensaje", recuperacion.solicitar(body.get("correo"), http.getRemoteAddr())));
    }

    /** {"correo", "codigo", "nuevaPassword"} → cambia la contraseña y cierra todas las sesiones. */
    @PostMapping("/confirmar")
    public ResponseEntity<Map<String, String>> confirmar(@RequestBody Map<String, String> body, HttpServletRequest http) {
        return ResponseEntity.ok(Map.of("mensaje", recuperacion.confirmar(body.get("correo"), body.get("codigo"),
                body.get("nuevaPassword"), http.getRemoteAddr())));
    }
}
