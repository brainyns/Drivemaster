package com.drivemaster.drivemaster.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validación de la ubicación (latitud/longitud) de un negocio. */
public final class Coordenadas {

    private Coordenadas() {
    }

    /**
     * Las coordenadas van las dos o ninguna, y dentro de rango. Con {@code obligatoria}
     * también se rechaza que falten (registro de un taller nuevo).
     */
    public static void validar(Double latitud, Double longitud, boolean obligatoria) {
        if (latitud == null && longitud == null) {
            if (obligatoria) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marca la ubicación del taller en el mapa");
            }
            return;
        }
        if (latitud == null || longitud == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica latitud y longitud");
        }
        if (latitud.isNaN() || latitud < -90 || latitud > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La latitud debe estar entre -90 y 90");
        }
        if (longitud.isNaN() || longitud < -180 || longitud > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La longitud debe estar entre -180 y 180");
        }
    }
}
