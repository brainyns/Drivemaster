package com.drivemaster.drivemaster.service.dispersion;

/**
 * El pago salió hacia Wompi pero no se sabe si quedó creado (tiempo vencido, error 5xx, respuesta vacía). No es un
 * fallo: reintentar podría pagar dos veces, así que la línea sigue PROCESANDO hasta que lo aclare el evento de Wompi
 * o su panel.
 */
public class DispersionInciertaException extends DispersionException {

    private final String referencia;

    public DispersionInciertaException(String mensaje, String referencia, Throwable causa) {
        super(mensaje, causa);
        this.referencia = referencia;
    }

    /** Referencia enviada a Wompi: con ella se reconoce el evento que resuelve la línea. */
    public String getReferencia() {
        return referencia;
    }
}
