package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactorExplicativoDTO {
    private String factor;
    private String titulo;
    private String descripcion;
    private Double impacto;
    private String tipo; // "FAVOR" o "RIESGO"
}

