package com.drivemaster.drivemaster.model;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Un paso en la línea de tiempo de una orden del marketplace: qué pasó, cuándo y
 * quién lo hizo. Es el avance que ve el cliente y la base del historial del vehículo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoOrden {

    // Estado de la orden después del evento (igual al anterior si no hubo cambio de estado)
    private String estado;
    private LocalDateTime fecha;

    // CLIENTE, TALLER, MECANICO, ADMIN o SISTEMA
    private String actorRol;
    private String actorId;
    private String actorNombre;

    private String nota;
}
