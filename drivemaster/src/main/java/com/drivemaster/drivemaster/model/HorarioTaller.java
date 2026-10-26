package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalTime;

/**
 * Una franja del horario de atención de un taller. Un día abierto tiene una o más franjas (orden 0, 1… por ejemplo
 * 8:00–12:00 y 14:00–18:00 con la pausa del almuerzo); un día cerrado se guarda como una sola fila con abierto=false y
 * sin horas. Lo escribe completo HorarioTallerService (PUT reemplaza todo el horario).
 */
@Document(collection = "horarios_taller")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioTaller {

    @Id
    private String id;

    // Indexado por IndicesMongo (la creación automática de índices está apagada)
    private String tallerId;

    private DiaSemana diaSemana;
    private boolean abierto;
    private LocalTime horaApertura;
    private LocalTime horaCierre;

    // Posición de la franja dentro del día (0 = la primera)
    private int orden;
}
