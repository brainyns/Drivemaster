package com.drivemaster.drivemaster.service.dispersion;

/** Lo que dice Wompi del intento anterior de una línea, antes de enviar otro. idWompi: el del pago, si se conoce. */
public record PagoAnterior(Estado estado, String idWompi) {

    public enum Estado { SALIO, NO_SALIO, SIN_SABER }

    public static final PagoAnterior SIN_SABER = new PagoAnterior(Estado.SIN_SABER, null);
}
