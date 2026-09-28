package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigMantenimientoRequest {

    /** Cada cuántos km se recomienda mantenimiento. */
    private Integer intervaloKm;

    /** null deja el valor actual; true/false activa o desactiva los recordatorios. */
    private Boolean recordatoriosActivos;
}
