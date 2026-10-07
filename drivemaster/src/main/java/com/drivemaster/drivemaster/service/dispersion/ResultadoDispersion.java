package com.drivemaster.drivemaster.service.dispersion;

import java.math.BigDecimal;

/** Lo que quedó registrado de un pago: modo, referencia y monto en pesos enteros. */
public record ResultadoDispersion(String modo, String referencia, BigDecimal monto) {
}
