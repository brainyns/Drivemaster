package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.CotizacionDTO;
import com.drivemaster.drivemaster.dto.CrearCotizacionRequest;
import com.drivemaster.drivemaster.dto.RespuestaCotizacionRequest;
import com.drivemaster.drivemaster.security.LimiteIntentos;
import com.drivemaster.drivemaster.service.CotizacionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cotizaciones del marketplace. Los endpoints /publica reciben el token del correo
 * en el cuerpo (nunca en la URL, para que no quede en logs) y tienen límite de
 * intentos por IP. El token no se registra en ningún log.
 */
@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    private final CotizacionService service;
    private final LimiteIntentos limiteIntentos;

    public CotizacionController(CotizacionService service, LimiteIntentos limiteIntentos) {
        this.service = service;
        this.limiteIntentos = limiteIntentos;
    }

    // ─── Taller ──────────────────────────────────────────────────────────────

    @PostMapping("/orden/{ordenId}/previsualizar")
    public ResponseEntity<CotizacionDTO> previsualizar(@PathVariable String ordenId,
                                                       @RequestBody CrearCotizacionRequest request,
                                                       Authentication auth) {
        return ResponseEntity.ok(service.previsualizar(auth.getName(), ordenId, request));
    }

    @PostMapping("/orden/{ordenId}")
    public ResponseEntity<CotizacionDTO> emitir(@PathVariable String ordenId,
                                                @RequestBody CrearCotizacionRequest request,
                                                Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.emitir(auth.getName(), ordenId, request));
    }

    // ─── Con sesión ──────────────────────────────────────────────────────────

    @GetMapping("/orden/{ordenId}")
    public ResponseEntity<List<CotizacionDTO>> deOrden(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(service.deOrden(auth.getName(), ordenId));
    }

    @PostMapping("/{id}/responder")
    public ResponseEntity<CotizacionDTO> responder(@PathVariable String id,
                                                   @RequestBody RespuestaCotizacionRequest request,
                                                   Authentication auth) {
        return ResponseEntity.ok(service.responder(auth.getName(), id, request));
    }

    // ─── Enlace del correo (sin sesión) ──────────────────────────────────────

    @PostMapping("/publica/consultar")
    public ResponseEntity<CotizacionDTO> consultarPublica(@RequestBody RespuestaCotizacionRequest request,
                                                          HttpServletRequest http) {
        limiteIntentos.consumir("cotizacion:" + http.getRemoteAddr());
        return ResponseEntity.ok(service.consultarPublica(request.getToken()));
    }

    @PostMapping("/publica/responder")
    public ResponseEntity<CotizacionDTO> responderPublica(@RequestBody RespuestaCotizacionRequest request,
                                                          HttpServletRequest http) {
        limiteIntentos.consumir("cotizacion:" + http.getRemoteAddr());
        return ResponseEntity.ok(service.responderPublica(request));
    }
}
