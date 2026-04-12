package com.drivemaster.drivemaster.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCompra {

    private String productoId;
    private String nombre;
    private Double costo;
    private Integer cantidad;
    private Double subtotal;
}
