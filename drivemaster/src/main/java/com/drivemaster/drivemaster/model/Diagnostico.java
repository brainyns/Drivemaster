package com.drivemaster.drivemaster.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Diagnóstico que registra el mecánico tras revisar el vehículo. Va embebido en
 * la OrdenServicio: es 1 a 1 con la orden y siempre se lee junto con ella.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnostico {

    public static final Set<String> URGENCIAS = Set.of("BAJA", "MEDIA", "ALTA", "CRITICA");

    private String hallazgos;

    @Builder.Default
    private List<String> fallas = new ArrayList<>();

    // BAJA, MEDIA, ALTA, CRITICA
    private String urgencia;

    private String observaciones;

    @Builder.Default
    private List<String> fotos = new ArrayList<>();

    private String mecanicoId;
    private String mecanicoNombre;
    private LocalDateTime fecha;
}
