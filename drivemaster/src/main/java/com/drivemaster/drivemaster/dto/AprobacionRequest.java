package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Decisión del ADMIN sobre un taller o tienda pendiente. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionRequest {

    private Boolean aprobado;

    // Obligatorio al rechazar; se le muestra al negocio.
    private String motivo;

    // Plan con el que arranca al aprobarse. Si no se indica, PRUEBA.
    private String planCodigo;
}
