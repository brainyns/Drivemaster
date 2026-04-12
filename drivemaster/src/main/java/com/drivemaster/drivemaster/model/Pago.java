package com.drivemaster.drivemaster.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    private String metodo; // EFECTIVO, TARJETA, TRANSFERENCIA
    private Double monto;
    private LocalDateTime fecha;
    private String referencia;
}
