package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Informes;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.service.InformesTallerService;
import com.drivemaster.drivemaster.service.TallerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Informes del taller de la sesión. El taller sale de la sesión, nunca de un parámetro: cada taller recibe únicamente
 * sus propios datos.
 */
@RestController
@RequestMapping("/api/talleres/mi-taller/informes")
public class InformesTallerController {

    private final InformesTallerService informes;
    private final TallerService tallerService;

    public InformesTallerController(InformesTallerService informes, TallerService tallerService) {
        this.informes = informes;
        this.tallerService = tallerService;
    }

    @GetMapping
    public ResponseEntity<Informes> informes(@RequestParam(defaultValue = "6") int meses, Authentication auth) {
        Taller taller = tallerService.obtenerPorUsuario(auth.getName());
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller aún no está aprobado");
        }
        return ResponseEntity.ok(informes.informes(taller.getId(), meses));
    }
}
