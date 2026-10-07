package com.drivemaster.drivemaster.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculos de dinero en pesos enteros (COP). Los montos se guardan como Double (las
 * columnas existentes no cambian), pero se suman y restan aquí con BigDecimal para
 * que los repartos no acumulen error de coma flotante.
 */
public final class Dinero {

    private Dinero() {
    }

    /** Pesos enteros, redondeo half-up. null cuenta como cero. */
    public static BigDecimal pesos(Double monto) {
        return monto == null ? BigDecimal.ZERO : BigDecimal.valueOf(monto).setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal pesos(BigDecimal monto) {
        return monto == null ? BigDecimal.ZERO : monto.setScale(0, RoundingMode.HALF_UP);
    }

    public static double aDouble(BigDecimal monto) {
        return monto.doubleValue();
    }
}
