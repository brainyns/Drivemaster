package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** El taller acepta la solicitud y propone la cita de revisión. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AceptarSolicitudRequest {

    // Fecha y hora (Colombia) en que el vehículo debe llegar al taller
    private LocalDateTime fechaRevision;

    // Opcional al aceptar; obligatorio antes de registrar el diagnóstico
    private String mecanicoId;
}
