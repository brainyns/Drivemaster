package com.drivemaster.drivemaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Cuenta bancaria registrada por un taller o una tienda para recibir dispersiones
 * automáticas (por Wompi Pagos a Terceros) o transferencias del marketplace.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaBancaria {

    private String banco;
    private String bancoCodigo;
    private String tipoCuenta;
    private String numeroCuenta;
    private String titularNombre;
    private String titularDocumentoTipo;
    private String titularDocumentoNumero;
    private LocalDateTime fechaActualizacion;
}

