package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Lista completa de repuestos elegidos para la orden (reemplaza la anterior). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeleccionRepuestosRequest {

    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        // Qué necesita el vehículo (ej. "Pastillas de freno delanteras")
        private String descripcion;
        private Integer cantidad;
        // Oferta elegida en el buscador
        private String productoId;
        // Obligatorio (true) si el producto es de compatibilidad POR_CONFIRMAR
        private Boolean confirmoCompatibilidad;
    }
}
