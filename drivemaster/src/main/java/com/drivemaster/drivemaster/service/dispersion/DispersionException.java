package com.drivemaster.drivemaster.service.dispersion;

/** La dispersión no se pudo ejecutar; la línea queda FALLIDO y el ADMIN puede reintentar. */
public class DispersionException extends RuntimeException {
    public DispersionException(String mensaje) {
        super(mensaje);
    }

    public DispersionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
