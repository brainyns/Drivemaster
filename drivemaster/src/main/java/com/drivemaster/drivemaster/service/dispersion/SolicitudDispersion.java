package com.drivemaster.drivemaster.service.dispersion;

/** Quién pide el pago (correo del ADMIN o SISTEMA) y, en modo MANUAL, el número de transferencia. */
public record SolicitudDispersion(String actor, String numeroTransferencia, String nota) {

    public static final String SISTEMA = "SISTEMA";

    public static SolicitudDispersion automatica() {
        return new SolicitudDispersion(SISTEMA, null, null);
    }
}
