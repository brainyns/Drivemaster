package com.drivemaster.drivemaster.dto.taller;

import lombok.Data;

/**
 * Lo único que el taller puede cambiar por su cuenta: la tarifa por hora, el horario y el punto exacto del local en
 * el mapa (latitud y longitud; la dirección escrita la sigue cambiando el administrador). Cualquier otro campo del
 * cuerpo (id, nombre, ciudad, estadoAprobacion...) no existe aquí y se descarta al leer el JSON.
 */
@Data
public class ActualizarDatosTallerRequest {
    private Double tarifaHora;
    private String horario;
    private Double latitud;
    private Double longitud;
}
