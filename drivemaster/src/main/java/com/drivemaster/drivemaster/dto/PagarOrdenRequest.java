package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagarOrdenRequest {

    /**
     * EFECTIVO o TRANSFERENCIA. Sin pasarela integrada, el pago con tarjeta en
     * línea se resuelve por Wompi en el flujo web, no aquí.
     */
    private String metodo;

    private String referencia;
}
