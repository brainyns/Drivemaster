package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrediccionAprobacionDTO {
    private Double probabilidadAprobacion;
    private Double probabilidadRechazo;
    private String prediccion;
    private String nivelRiesgo; // BAJO, MEDIO, ALTO
    private List<String> recomendaciones;
    private String origen; // PYTHON_API o FALLBACK_JAVA
}

