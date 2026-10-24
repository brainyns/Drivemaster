package com.drivemaster.drivemaster.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Al recuperar la contraseña se cierran todas las sesiones: los tokens emitidos antes ya no valen. */
class SesionesCerradasTest {

    private JwtUtil jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "clave-de-prueba-de-al-menos-32-bytes-123456");
        ReflectionTestUtils.setField(jwt, "expirationMs", 600_000L);
        ReflectionTestUtils.setField(jwt, "refreshExpirationMs", 600_000L);
    }

    private static DetallesUsuario ana(Instant sesionesDesde) {
        return new DetallesUsuario("ana@demo.com", "x", true, List.of(new SimpleGrantedAuthority("ROLE_TALLER")), sesionesDesde);
    }

    @Test
    void unTokenEmitidoAntesDelCierreYaNoVale() {
        String viejo = jwt.generateToken(ana(null));
        String refrescoViejo = jwt.generateRefreshToken(ana(null));
        Instant cierre = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);

        assertFalse(jwt.validateToken(viejo, ana(cierre)));
        assertTrue(jwt.emitidoAntesDe(refrescoViejo, cierre), "tampoco sirve para renovar");
    }

    @Test
    void laSesionNuevaDelMismoSegundoSiVale() {
        Instant cierre = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String nuevo = jwt.generateToken(ana(cierre));
        assertTrue(jwt.validateToken(nuevo, ana(cierre)));
    }

    @Test
    void sinCierreTodoSigueIgual() {
        String token = jwt.generateToken(ana(null));
        assertTrue(jwt.validateToken(token, ana(null)));
        assertTrue(jwt.validateToken(token, User.withUsername("ana@demo.com").password("x").authorities("ROLE_TALLER").build()));
    }
}
