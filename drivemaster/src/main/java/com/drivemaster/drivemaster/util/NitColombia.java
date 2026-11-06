package com.drivemaster.drivemaster.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * NIT colombiano de un taller o una tienda: de 6 a 10 dígitos (el de una persona natural es su cédula) y, si se
 * escribe, el dígito de verificación después de un guion: 900123456-7 o 900123456. Se aceptan puntos y espacios
 * (900.123.456-7) y se guarda sin ellos. El dígito de verificación no se recalcula: solo se exige el formato.
 */
public final class NitColombia {

    public static final String MENSAJE = "Escribe el NIT con números y, si lo tienes, el dígito de verificación después "
            + "de un guion (ej. 900123456-7).";
    public static final String OBLIGATORIO = "El NIT es obligatorio. " + MENSAJE;

    private NitColombia() {
    }

    /** El NIT sin puntos ni espacios ("900123456-7"), o null si no tiene el formato. */
    public static String normalizar(String nit) {
        if (nit == null) return null;
        String limpio = nit.trim().replaceAll("[\\s.]", "");
        return limpio.matches("\\d{6,10}(-\\d)?") ? limpio : null;
    }

    /** Como normalizar, pero responde 400 con un mensaje claro si falta o no sirve. */
    public static String validar(String nit) {
        if (nit == null || nit.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, OBLIGATORIO);
        String limpio = normalizar(nit);
        if (limpio == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAJE);
        return limpio;
    }

    /**
     * Si dos NIT son el mismo negocio: iguales sus dígitos (900123456-7 = 9001234567) o su número sin el dígito de
     * verificación (900123456-7 = 900123456). null o sin dígitos nunca coincide.
     */
    public static boolean mismo(String a, String b) {
        String da = digitos(a);
        String db = digitos(b);
        if (da == null || db == null) return false;
        return da.equals(db) || base(a).equals(base(b));
    }

    private static String digitos(String nit) {
        if (nit == null) return null;
        String d = nit.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String base(String nit) {
        String numero = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
        return numero.replaceAll("\\D", "");
    }
}
