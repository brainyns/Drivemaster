package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.taller.ActualizarDatosTallerRequest;
import com.drivemaster.drivemaster.service.DatosTallerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** El taller edita su tarifa por hora, su horario, su ubicación en el mapa y su teléfono. El taller es el de la sesión, nunca uno que venga en el cuerpo. */
@RestController
@RequestMapping("/api/talleres/mi-taller")
public class DatosTallerController {

    private final DatosTallerService datos;

    public DatosTallerController(DatosTallerService datos) {
        this.datos = datos;
    }

    @PatchMapping("/datos")
    public ResponseEntity<TallerDTO> actualizar(@RequestBody ActualizarDatosTallerRequest request, Authentication auth) {
        return ResponseEntity.ok(datos.actualizar(auth.getName(), request));
    }

    /** Solo el teléfono del taller de la sesión: {"telefono": "300 123 4567"}. */
    @PutMapping("/telefono")
    public ResponseEntity<TallerDTO> telefono(@RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(datos.actualizarTelefono(auth.getName(), body == null ? null : body.get("telefono")));
    }
}
