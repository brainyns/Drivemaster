package com.drivemaster.drivemaster.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {

    private String productoId;
    private String nombre;
    private String imagenUrl;
    private Double precioUnitario;
    private Integer cantidad;
    private Double subtotal;
    private String tipo; // STOCK | ENCARGO
    private Integer stockActual;
}
