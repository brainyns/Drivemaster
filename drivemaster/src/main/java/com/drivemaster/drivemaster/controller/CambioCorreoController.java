package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.AuthResponse;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.security.CustomUserDetailsService;
import com.drivemaster.drivemaster.security.JwtUtil;
import com.drivemaster.drivemaster.service.CambioCorreoService;
import com.drivemaster.drivemaster.util.Ciudades;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Cambio del correo de la cuenta del taller o la tienda de la sesión (ver CambioCorreoService). Nunca recibe de quién
 * es la cuenta: siempre es la del token.
 */
@RestController
@RequestMapping("/api/cuenta/correo")
public class CambioCorreoController {

    private final CambioCorreoService cambioCorreo;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public CambioCorreoController(CambioCorreoService cambioCorreo, CustomUserDetailsService userDetailsService,
                                  JwtUtil jwtUtil) {
        this.cambioCorreo = cambioCorreo;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    /** {"passwordActual": "...", "correoNuevo": "..."} → envía el código al correo nuevo. */
    @PostMapping("/solicitar")
    public ResponseEntity<CambioCorreoService.Pendiente> solicitar(@RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(cambioCorreo.solicitar(auth.getName(), body.get("passwordActual"), body.get("correoNuevo")));
    }

    /**
     * {"codigo": "123456"} → cambia el correo y devuelve una sesión nueva: el token lleva el correo, así que el
     * anterior deja de servir.
     */
    @PostMapping("/confirmar")
    public ResponseEntity<AuthResponse> confirmar(@RequestBody Map<String, String> body, Authentication auth) {
        Usuario usuario = cambioCorreo.confirmar(auth.getName(), body.get("codigo"));
        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getCorreo());
        return ResponseEntity.ok(new AuthResponse(
                jwtUtil.generateToken(userDetails), usuario.getId(), usuario.getNombre(),
                usuario.getCorreo(), usuario.getRol(),
                usuario.getProveedor(), usuario.getDatosCompletos(),
                jwtUtil.generateRefreshToken(userDetails), Ciudades.pendiente(usuario)));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancelar(Authentication auth) {
        cambioCorreo.cancelar(auth.getName());
        return ResponseEntity.noContent().build();
    }
}
