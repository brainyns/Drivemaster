package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Registro de un cambio que el taller hizo a sus propios datos: qué cambió, quién, cuándo y el valor anterior. */
@Document(collection = "cambios_taller")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambioTaller {

    public static final String TARIFA_HORA = "tarifaHora";
    public static final String HORARIO = "horario";
    public static final String UBICACION = "ubicacion";

    @Id
    private String id;

    @Indexed
    private String tallerId;

    private String campo;
    private String valorAnterior;
    private String valorNuevo;

    /** Correo de la sesión que hizo el cambio. */
    private String cambiadoPor;
    private LocalDateTime fecha;
}
