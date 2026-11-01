package com.drivemaster.drivemaster.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 409 de una línea que ya se está pagando o ya se pagó. Lleva su estado para que la pantalla muestre lo que pasó con
 * el pago en vez de un error. sinConfirmar: Wompi no confirmó y la línea espera su evento o una resolución manual.
 */
public class RepartoYaEnPagoException extends ResponseStatusException {
    private static final long serialVersionUID = 1L;

    private final String estado;
    private final String referencia;
    private final boolean sinConfirmar;

    public RepartoYaEnPagoException(String mensaje, String estado, String referencia, boolean sinConfirmar) {
        super(HttpStatus.CONFLICT, mensaje);
        this.estado = estado;
        this.referencia = referencia;
        this.sinConfirmar = sinConfirmar;
    }

    public String getEstado() { return estado; }
    public String getReferencia() { return referencia; }
    public boolean isSinConfirmar() { return sinConfirmar; }
}
