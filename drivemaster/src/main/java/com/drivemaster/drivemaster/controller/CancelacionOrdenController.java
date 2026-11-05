package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.CancelacionOrdenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** SUPERADMIN (por /api/plataforma/**): cancela una orden pagada, antes de la instalación, con motivo. */
@RestController
@RequestMapping("/api/plataforma/ordenes")
public class CancelacionOrdenController {

    private final CancelacionOrdenService cancelacion;

    public CancelacionOrdenController(CancelacionOrdenService cancelacion) {
        this.cancelacion = cancelacion;
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<CancelacionOrdenService.Resultado> cancelar(@PathVariable String id,
                                                                      @RequestBody(required = false) Map<String, String> body,
                                                                      Authentication auth) {
        return ResponseEntity.ok(cancelacion.cancelar(auth.getName(), id, body != null ? body.get("motivo") : null));
    }
}
