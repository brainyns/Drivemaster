package com.drivemaster.drivemaster.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de un solo uso para los enlaces de correo (ej. responder una cotización
 * sin iniciar sesión). Solo se guarda el hash SHA-256: si la base se filtra, los
 * tokens no sirven. Nunca se escriben en los logs.
 */
public final class TokenSeguro {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private TokenSeguro() {
    }

    /** token: lo que va en el enlace; hash: lo que se guarda. */
    public record Generado(String token, String hash) {
    }

    public static Generado generar() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Generado(token, hash(token));
    }

    public static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Formato válido antes de consultar la base (evita búsquedas con basura). */
    public static boolean formatoValido(String token) {
        return token != null && token.length() == 43 && token.matches("[A-Za-z0-9_-]+");
    }
}
