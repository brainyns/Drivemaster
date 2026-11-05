package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Tienda;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Descuento que una tienda da a los talleres aprobados en el catálogo. Lo fija la tienda (0 a 50 %); si nunca lo tocó,
 * vale el inicial. Los clientes pagan siempre el precio público.
 */
public final class DescuentoTaller {

    public static final double INICIAL_PCT = 20;
    public static final double MAXIMO_PCT = 50;

    private DescuentoTaller() {
    }

    public static double pct(Tienda tienda) {
        return tienda == null || tienda.getDescuentoTalleresPct() == null ? INICIAL_PCT : tienda.getDescuentoTalleresPct();
    }

    /** Precio con el descuento, en pesos enteros. */
    public static double precio(double publico, double pct) {
        return BigDecimal.valueOf(publico).multiply(BigDecimal.valueOf(100 - pct))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).doubleValue();
    }

    public static double validar(Double pct) {
        if (pct == null || pct.isNaN() || pct < 0 || pct > MAXIMO_PCT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento para talleres va de 0 a " + (int) MAXIMO_PCT + " %");
        }
        return BigDecimal.valueOf(pct).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
