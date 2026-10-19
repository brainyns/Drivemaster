package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.LogoTallerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Logo del taller. /mi-taller/logo es solo del TALLER y siempre sobre su propio taller; /{id}/logo es del ADMIN y el
 * SUPERADMIN (reglas en SecurityConfig). Responde la URL guardada, como la foto del vehículo.
 */
@RestController
@RequestMapping("/api/talleres")
public class LogoTallerController {

    private final LogoTallerService logos;

    public LogoTallerController(LogoTallerService logos) {
        this.logos = logos;
    }

    @PostMapping("/mi-taller/logo")
    public ResponseEntity<Map<String, String>> subirMio(@RequestParam("archivo") MultipartFile archivo, Authentication auth) {
        return ResponseEntity.ok(Map.of("logoUrl", logos.subirMio(auth.getName(), archivo)));
    }

    @PostMapping("/{id}/logo")
    public ResponseEntity<Map<String, String>> subirPorAdmin(@PathVariable String id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(Map.of("logoUrl", logos.subirPorAdmin(id, archivo)));
    }
}
