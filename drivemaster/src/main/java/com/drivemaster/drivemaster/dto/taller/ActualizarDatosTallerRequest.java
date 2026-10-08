package com.drivemaster.drivemaster.dto.taller;

import lombok.Data;

/**
 * Lo único que el taller puede cambiar por su cuenta: la tarifa por hora y el horario. Cualquier otro campo del
 * cuerpo (id, nombre, ciudad, estadoAprobacion...) no existe aquí y se descarta al leer el JSON.
 */
@Data
public class ActualizarDatosTallerRequest {
    private Double tarifaHora;
    private String horario;
}
