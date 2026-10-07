package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Diagnóstico que registra el mecánico (o el taller por él en v1). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticoRequest {

    private String hallazgos;
    private List<String> fallas;

    // BAJA, MEDIA, ALTA, CRITICA
    private String urgencia;
    private String observaciones;

    // URLs devueltas por POST /api/solicitudes-servicio/fotos
    private List<String> fotos;

    // Opcional si la orden ya tiene mecánico asignado
    private String mecanicoId;

    // Repuestos necesarios (se guardan junto con el diagnóstico). Null = no cambiar
    // la selección actual; lista vacía = solo mano de obra.
    private List<SeleccionRepuestosRequest.Item> repuestos;
}
