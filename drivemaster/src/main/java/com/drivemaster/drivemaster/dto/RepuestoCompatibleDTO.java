package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepuestoCompatibleDTO {
    private String productoId;
    private String codigo;
    private String nombre;
    private String categoria;
    private String marca;
    private Double precioVenta;
    private Integer stockActual;
    private String imagenUrl;
    // La tienda que lo vende
    private String tiendaId;
    private String tiendaNombre;
    private String tiendaCiudad;
}