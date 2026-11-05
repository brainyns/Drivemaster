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

    private static final java.util.Locale CO = java.util.Locale.forLanguageTag("es-CO");

    /** "$ 123.456": pesos enteros con punto de miles, como se ven en la app. */
    public static String texto(BigDecimal valor) {
        java.text.NumberFormat f = java.text.NumberFormat.getIntegerInstance(CO);
        return "$ " + f.format(valor == null ? BigDecimal.ZERO : valor.setScale(0, RoundingMode.HALF_UP));
    }

    /** "3,4": un número con hasta un decimal y coma, para kilómetros u horas en textos que lee una persona. */
    public static String decimal(double valor) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(CO);
        f.setMaximumFractionDigits(1);
        f.setMinimumFractionDigits(0);
        f.setRoundingMode(RoundingMode.HALF_UP); // como el navegador: 41,25 → 41,3
        return f.format(valor);
    }
}
