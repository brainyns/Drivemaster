package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.TallerCercanoDTO;
import com.drivemaster.drivemaster.service.TalleresCercanosService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Talleres más cercanos al punto del cliente, con el tiempo en carro de los primeros (mapa del diagnóstico). */
@RestController
@RequestMapping("/api/talleres/cercanos")
public class TalleresCercanosController {

    private final TalleresCercanosService cercanos;

    public TalleresCercanosController(TalleresCercanosService cercanos) {
        this.cercanos = cercanos;
    }

    @GetMapping
    public ResponseEntity<List<TallerCercanoDTO>> listar(@RequestParam double lat, @RequestParam double lng,
                                                         @RequestParam(defaultValue = "3") int limite) {
        return ResponseEntity.ok(cercanos.cercanos(lat, lng, limite));
    }
}
