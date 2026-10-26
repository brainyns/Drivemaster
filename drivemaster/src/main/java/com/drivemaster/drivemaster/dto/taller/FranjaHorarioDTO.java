package com.drivemaster.drivemaster.dto.taller;

import com.drivemaster.drivemaster.model.DiaSemana;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** Una franja del horario tal como viaja por /api/talleres/{id}/horario. Las horas van como "HH:mm" (24 h). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FranjaHorarioDTO {
    private DiaSemana diaSemana;
    private boolean abierto;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaApertura;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaCierre;

    private int orden;
}
