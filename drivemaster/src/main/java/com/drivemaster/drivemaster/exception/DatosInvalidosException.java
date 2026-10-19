package com.drivemaster.drivemaster.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos de un formulario que no cumplen las reglas, con el error de cada campo para que el frontend lo muestre junto
 * a él. GlobalExceptionHandler responde 400 con { error, campos: { campo: mensaje } }.
 */
public class DatosInvalidosException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Map<String, String> campos;

    public DatosInvalidosException(Map<String, String> campos) {
        super(campos.size() == 1 ? campos.values().iterator().next() : "Revisa los datos marcados");
        this.campos = Collections.unmodifiableMap(new LinkedHashMap<>(campos));
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
