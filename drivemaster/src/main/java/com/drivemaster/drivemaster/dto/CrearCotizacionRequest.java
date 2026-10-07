package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lo que el taller completa para cotizar. Los repuestos salen de la selección de la
 * orden (con el precio de la tienda congelado al emitir); aquí va la mano de obra.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearCotizacionRequest {

    private List<ManoObra> manoObra;

    // Duración estimada del trabajo en el taller (horas)
    private Integer tiempoEstimadoHoras;
    private String observaciones;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManoObra {
        private String descripcion;
        // ITEM (valor fijo) o HORAS (horas × tarifa por hora del taller)
        private String modalidad;
        private Double valor;
        private Double horas;
    }
}
