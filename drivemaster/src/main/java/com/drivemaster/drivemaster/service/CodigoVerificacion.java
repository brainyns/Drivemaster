package com.drivemaster.drivemaster.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Código de 6 dígitos que se envía por correo para confirmar una acción (cambio de correo, recuperación de
 * contraseña): vence en 15 minutos, admite 5 intentos y solo se guarda su hash (BCrypt), así que si la base se
 * filtra el código no sirve.
 */
@Component
public class CodigoVerificacion {

    public static final Duration VIGENCIA = Duration.ofMinutes(15);
    public static final int MAX_INTENTOS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordEncoder passwordEncoder;

    public CodigoVerificacion(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /** Lo que guarda cada flujo mientras espera el código. */
    public interface Pendiente {
        Instant getVence();
        int getIntentos();
        void setIntentos(int intentos);
        String getCodigoHash();
    }

    /** codigo: lo que va en el correo; hash: lo que se guarda. */
    public record Generado(String codigo, String hash) {
    }

    public Generado generar() {
        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        return new Generado(codigo, passwordEncoder.encode(codigo));
    }

    /**
     * Revisa vencimiento, intentos y código. Si no sirve responde 400 con un mensaje para mostrar: llama a guardar
     * cuando solo sumó un intento y a descartar cuando el pendiente ya no sirve (vencido o sin intentos).
     */
    public void verificar(Pendiente pendiente, String codigo, Runnable guardar, Runnable descartar) {
        if (Instant.now().isAfter(pendiente.getVence())) {
            descartar.run();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código venció. Pide uno nuevo");
        }
        if (pendiente.getIntentos() >= MAX_INTENTOS) {
            descartar.run();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agotaste los " + MAX_INTENTOS + " intentos. Pide un código nuevo");
        }
        String limpio = codigo == null ? "" : codigo.replaceAll("\\s+", "");
        if (limpio.matches("\\d{6}") && passwordEncoder.matches(limpio, pendiente.getCodigoHash())) return;

        pendiente.setIntentos(pendiente.getIntentos() + 1);
        int quedan = MAX_INTENTOS - pendiente.getIntentos();
        if (quedan <= 0) {
            descartar.run();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Código incorrecto. Agotaste los " + MAX_INTENTOS + " intentos: pide un código nuevo");
        }
        guardar.run();
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Código incorrecto. Te " + (quedan == 1 ? "queda 1 intento" : "quedan " + quedan + " intentos"));
    }
}
