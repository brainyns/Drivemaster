package com.drivemaster.drivemaster.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Código con el que el cliente retira una compra del catálogo en la tienda. Aleatorio
 * (SecureRandom), de 8 caracteres de un alfabeto sin letras confundibles (40 bits), pensado
 * para dictarse en un mostrador. Solo se guarda su hash SHA-256 ligado al pedido: si la base
 * se filtra, los códigos no sirven; el código en claro solo se muestra al cliente en el
 * momento de generarlo. Nunca se escribe en los logs.
 *
 * Como es corto, quien lo valida lleva un límite de intentos por pedido (ver PedidoTiendaService).
 */
public final class CodigoRetiro {

    // Crockford base32: sin I, L, O ni U
    private static final String ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LONGITUD = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoRetiro() {
    }

    /** Lo que ve el cliente, con guion para leerlo: XXXX-XXXX. */
    public static String generar() {
        StringBuilder sb = new StringBuilder(LONGITUD + 1);
        for (int i = 0; i < LONGITUD; i++) {
            if (i == LONGITUD / 2) sb.append('-');
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /**
     * Quita espacios y guiones, pasa a mayúsculas y corrige las confusiones típicas (O→0, I/L→1).
     * Devuelve null si no tiene la forma de un código.
     */
    public static String normalizar(String ingresado) {
        if (ingresado == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : ingresado.toUpperCase().toCharArray()) {
            if (c == ' ' || c == '-') continue;
            if (c == 'O') c = '0';
            if (c == 'I' || c == 'L') c = '1';
            if (ALFABETO.indexOf(c) < 0) return null;
            sb.append(c);
        }
        return sb.length() == LONGITUD ? sb.toString() : null;
    }

    /** Hash que se guarda: ligado al pedido, para que un código de un pedido no sirva en otro. */
    public static String hash(String pedidoId, String codigo) {
        String normalizado = normalizar(codigo);
        if (pedidoId == null || normalizado == null) throw new IllegalArgumentException("Código inválido");
        return sha256(pedidoId + ":" + normalizado);
    }

    /** Compara en tiempo constante. false si el código no tiene forma válida. */
    public static boolean coincide(String hashGuardado, String pedidoId, String ingresado) {
        String normalizado = normalizar(ingresado);
        if (hashGuardado == null || pedidoId == null || normalizado == null) return false;
        return MessageDigest.isEqual(
                hashGuardado.getBytes(StandardCharsets.UTF_8),
                sha256(pedidoId + ":" + normalizado).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
