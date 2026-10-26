package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO;
import com.drivemaster.drivemaster.service.HorarioTallerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Horario de atención estructurado de un taller. El taller solo ve y reemplaza el suyo; el personal lo consulta. */
@RestController
@RequestMapping("/api/talleres/{id}")
public class HorarioTallerController {

    private final HorarioTallerService horario;

    public HorarioTallerController(HorarioTallerService horario) {
        this.horario = horario;
    }

    @GetMapping("/horario")
    public ResponseEntity<List<FranjaHorarioDTO>> obtener(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(horario.obtener(id, auth.getName(), esPersonal(auth)));
    }

    @PutMapping("/horario")
    public ResponseEntity<List<FranjaHorarioDTO>> reemplazar(@PathVariable String id,
                                                             @RequestBody(required = false) List<FranjaHorarioDTO> franjas,
                                                             Authentication auth) {
        return ResponseEntity.ok(horario.reemplazar(id, auth.getName(), franjas));
    }

    static boolean esPersonal(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
