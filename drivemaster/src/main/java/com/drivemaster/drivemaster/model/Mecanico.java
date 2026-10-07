package com.drivemaster.drivemaster.model;

import lombok.*;

/**
 * Mecánico de un taller. Se guarda embebido en Taller: en v1 es solo un nombre
 * asignable a las órdenes. Cuando tenga login propio, usuarioId apuntará a un
 * Usuario con rol MECANICO sin necesidad de migrar datos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mecanico {

    private String id;
    private String nombre;
    private String especialidad;
    private String telefono;
    private Boolean activo;

    // Vacío en v1.
    private String usuarioId;
}
