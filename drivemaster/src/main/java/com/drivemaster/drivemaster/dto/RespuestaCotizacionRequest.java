package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Respuesta del cliente a una cotización (con sesión o con el token del correo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaCotizacionRequest {

    public static final String APROBAR = "APROBAR";
    public static final String RECHAZAR = "RECHAZAR";
    public static final String CAMBIOS = "CAMBIOS";

    // APROBAR, RECHAZAR o CAMBIOS
    private String accion;
    // Obligatorio al pedir cambios
    private String motivo;
    // Solo en el endpoint público (va en el cuerpo, nunca en la URL)
    private String token;
}
