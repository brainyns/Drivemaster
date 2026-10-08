package com.drivemaster.drivemaster.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Código con el que el cliente retira una compra del catálogo en la tienda. Aleatorio
 * (SecureRandom), de 8 caracteres de un alfabeto sin letras confundibles (40 bits), pensado
 * para dictarse en un mostrador. Solo se guarda un código de autenticación ligado al pedido:
 * HMAC-SHA256 con una clave secreta del servidor (RETIRO_CODIGO_SECRET), con el prefijo "hmac1:".
 * Como el código es corto, un SHA-256 simple se podría adivinar por fuerza bruta con la base filtrada;
 * con la clave del servidor, la base sola no basta. El código en claro solo se muestra al cliente en
 * el momento de generarlo. Nunca se escribe en los logs.
 *
 * Compatibilidad: los códigos emitidos antes (SHA-256 simple, 64 hex sin prefijo) siguen validando hasta que
 * se usen; son de un solo uso, así que no hace falta migrarlos. Si la clave no está configurada el backend
 * avisa al arrancar y los códigos nuevos usan el SHA-256 anterior (ver CodigoRetiroConfig).
 *
 * Como es corto, quien lo valida lleva un límite de intentos por pedido (ver PedidoTiendaService).
 */
public final class CodigoRetiro {

    // Crockford base32: sin I, L, O ni U
    private static final String ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LONGITUD = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Marca de los códigos guardados con HMAC; los anteriores no la tienen. */
    public static final String PREFIJO_HMAC = "hmac1:";

    // Clave del servidor; null = sin configurar. La fija CodigoRetiroConfig al arrancar.
    private static volatile byte[] secreto;

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

    /** Fija (o quita, con null o vacío) la clave secreta del servidor. */
    public static void configurar(String clave) {
        secreto = clave == null || clave.isBlank() ? null : clave.trim().getBytes(StandardCharsets.UTF_8);
    }

    public static boolean configurado() {
        return secreto != null;
    }

    /** true si el valor guardado es del esquema anterior (SHA-256 simple, sin prefijo). */
    public static boolean esLegado(String guardado) {
        return guardado != null && !guardado.startsWith(PREFIJO_HMAC);
    }

    /**
     * Valor que se guarda: ligado al pedido, para que un código de un pedido no sirva en otro. HMAC-SHA256 con la
     * clave del servidor; solo si la clave falta se usa el SHA-256 anterior.
     */
    public static String hash(String pedidoId, String codigo) {
        String normalizado = normalizar(codigo);
        if (pedidoId == null || normalizado == null) throw new IllegalArgumentException("Código inválido");
        String mensaje = pedidoId + ":" + normalizado;
        byte[] clave = secreto;
        return clave != null ? PREFIJO_HMAC + hmac(clave, mensaje) : sha256(mensaje);
    }

    /** Compara en tiempo constante. false si el código no tiene forma válida. */
    public static boolean coincide(String hashGuardado, String pedidoId, String ingresado) {
        String normalizado = normalizar(ingresado);
        if (hashGuardado == null || pedidoId == null || normalizado == null) return false;
        String mensaje = pedidoId + ":" + normalizado;
        String esperado;
        if (hashGuardado.startsWith(PREFIJO_HMAC)) {
            byte[] clave = secreto;
            if (clave == null) return false; // sin la clave del servidor no se puede verificar
            esperado = PREFIJO_HMAC + hmac(clave, mensaje);
        } else {
            esperado = sha256(mensaje); // emitido antes del HMAC
        }
        return MessageDigest.isEqual(hashGuardado.getBytes(StandardCharsets.UTF_8), esperado.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(byte[] clave, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
