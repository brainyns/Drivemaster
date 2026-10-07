package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** El cliente confirma que recibió el vehículo. Ambos campos son opcionales. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmarRecepcionRequest {
    // 1 a 5
    private Integer calificacion;
    private String comentario;
}
