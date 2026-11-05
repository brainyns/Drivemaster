package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Datos de la cuenta bancaria de un taller o una tienda.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaBancariaDTO {

    private String banco;
    private String bancoCodigo;
    private String tipoCuenta;
    private String numeroCuenta;
    private String titularNombre;
    private String titularDocumentoTipo;
    private String titularDocumentoNumero;
    private LocalDateTime fechaActualizacion;
}

