package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.LogoTiendaService;
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
 * Logo de la tienda, como el del taller (LogoTallerController). /mi-tienda/logo es solo de la TIENDA y siempre sobre
 * su propia tienda; /{id}/logo es del ADMIN y el SUPERADMIN (reglas en SecurityConfig). Responde la URL guardada.
 */
@RestController
@RequestMapping("/api/tiendas")
public class LogoTiendaController {

    private final LogoTiendaService logos;

    public LogoTiendaController(LogoTiendaService logos) {
        this.logos = logos;
    }

    @PostMapping("/mi-tienda/logo")
    public ResponseEntity<Map<String, String>> subirMio(@RequestParam("archivo") MultipartFile archivo, Authentication auth) {
        return ResponseEntity.ok(Map.of("logoUrl", logos.subirMio(auth.getName(), archivo)));
    }

    @PostMapping("/{id}/logo")
    public ResponseEntity<Map<String, String>> subirPorAdmin(@PathVariable String id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(Map.of("logoUrl", logos.subirPorAdmin(id, archivo)));
    }
}
