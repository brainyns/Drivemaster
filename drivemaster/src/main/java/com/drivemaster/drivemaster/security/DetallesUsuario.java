package com.drivemaster.drivemaster.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Collection;

/**
 * Usuario de Spring Security con el momento desde el que valen sus sesiones: JwtUtil.validateToken rechaza los tokens
 * emitidos antes (al recuperar la contraseña se cierran todas las sesiones abiertas).
 */
public class DetallesUsuario extends User {

    private final transient Instant sesionesDesde;

    public DetallesUsuario(String correo, String password, boolean activo,
                           Collection<? extends GrantedAuthority> permisos, Instant sesionesDesde) {
        super(correo, password, activo, true, true, true, permisos);
        this.sesionesDesde = sesionesDesde;
    }

    public Instant getSesionesDesde() {
        return sesionesDesde;
    }
}
