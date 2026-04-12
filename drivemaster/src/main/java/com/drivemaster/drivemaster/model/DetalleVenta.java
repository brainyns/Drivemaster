package com.drivemaster.drivemaster.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {

    private String productoId;
    private String nombre;
    private Double precioUnitario;
    private Integer cantidad;
    private Double subtotal;
}

