package com.drivemaster.drivemaster.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Motivo escrito de una cancelación: el mismo mínimo en todas (cliente, tienda, taller y administrador), para que el
 * historial siempre explique qué pasó. El frontend usa el mismo número (utils/motivo.js).
 */
public final class Motivos {

    public static final int MINIMO = 10;

    private Motivos() {
    }

    /**
     * El motivo sin espacios a los lados y recortado a maximo, o 400 si tiene menos de MINIMO caracteres.
     * queSeHace completa la frase del error: "Explica en al menos 10 caracteres por qué " + queSeHace.
     */
    public static String obligatorio(String motivo, String queSeHace, int maximo) {
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < MINIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Explica en al menos " + MINIMO + " caracteres por qué " + queSeHace);
        }
        return razon.length() > maximo ? razon.substring(0, maximo) : razon;
    }
}
