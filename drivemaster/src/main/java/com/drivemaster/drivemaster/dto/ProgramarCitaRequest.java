package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgramarCitaRequest {

    /** Fecha y hora en que el vehículo ingresará al taller. */
    private LocalDateTime fechaProgramada;
}
