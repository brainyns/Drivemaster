package com.drivemaster.drivemaster.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Teléfonos de Colombia con la numeración vigente: celular de 10 dígitos que empieza por 3, o fijo de 10 dígitos que
 * empieza por 60 más el indicativo de la región (601 Bogotá, 604 Medellín, 602 Cali…). Se aceptan espacios, guiones,
 * puntos, paréntesis y el prefijo +57; se guarda solo con los 10 dígitos.
 */
public final class TelefonoColombia {

    public static final String MENSAJE = "Escribe un celular de 10 dígitos que empiece por 3 (ej. 300 123 4567) "
            + "o un fijo de 10 dígitos que empiece por 60 (ej. 601 234 5678).";

    private TelefonoColombia() {
    }

    /** Los 10 dígitos del número, o null si no es un celular ni un fijo colombiano válido. */
    public static String normalizar(String telefono) {
        if (telefono == null) return null;
        String digitos = telefono.trim().replaceAll("[\\s().-]", "");
        if (digitos.startsWith("+57")) digitos = digitos.substring(3);
        else if (digitos.length() == 12 && digitos.startsWith("57")) digitos = digitos.substring(2);
        if (!digitos.matches("\\d{10}")) return null;
        return digitos.matches("3\\d{9}|60[1-8]\\d{7}") ? digitos : null;
    }

    /** Como normalizar, pero responde 400 con un mensaje claro si el número no sirve. */
    public static String validar(String telefono) {
        String numero = normalizar(telefono);
        if (numero == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAJE);
        return numero;
    }
}
