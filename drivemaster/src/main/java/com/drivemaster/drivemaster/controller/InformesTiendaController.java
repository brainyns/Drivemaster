package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Informes;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.InformesTiendaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informes de la TIENDA (solo ese rol: lo define SecurityConfig). La tienda sale de la sesión, nunca de un parámetro:
 * cada tienda recibe únicamente sus propios datos.
 */
@RestController
@RequestMapping("/api/tiendas/mi-tienda/informes")
public class InformesTiendaController {

    private final InformesTiendaService informes;
    private final AlcanceTienda alcanceTienda;

    public InformesTiendaController(InformesTiendaService informes, AlcanceTienda alcanceTienda) {
        this.informes = informes;
        this.alcanceTienda = alcanceTienda;
    }

    @GetMapping
    public ResponseEntity<Informes> informes(@RequestParam(defaultValue = "6") int meses, Authentication auth) {
        return ResponseEntity.ok(informes.informes(alcanceTienda.tienda(auth).getId(), meses));
    }
}
