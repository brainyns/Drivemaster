package com.drivemaster.drivemaster.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Forma única de los correos: sin espacios y en minúsculas. Se aplica al registrarse, al iniciar sesión y al cambiar
 * el correo, así "Ana@Gmail.com " y "ana@gmail.com" son la misma cuenta. Las cuentas antiguas guardadas con mayúsculas
 * se siguen encontrando (ver UsuarioServiceImpl.buscarPorCorreo y MIGRACIONES_PENDIENTES.md).
 */
public final class Correos {

    public static final int LARGO_MAXIMO = 254;
    private static final Pattern FORMATO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Correos() {
    }

    /** Sin espacios y en minúsculas; null si viene null. */
    public static String normalizar(String correo) {
        return correo == null ? null : correo.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** nombre@dominio.algo, sin espacios y de largo razonable (se valida ya normalizado). */
    public static boolean valido(String correo) {
        return correo != null && correo.length() <= LARGO_MAXIMO && FORMATO.matcher(correo).matches();
    }
}
