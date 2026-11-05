package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Textos que lee una persona: formato de Colombia (coma decimal, punto de miles). */
class DineroTextoTest {

    @Test
    void pesosConPuntoDeMiles() {
        assertEquals("$ 123.456", Dinero.texto(new BigDecimal("123456")));
        assertEquals("$ 1.234.568", Dinero.texto(new BigDecimal("1234567.6")));
        assertEquals("$ 0", Dinero.texto(null));
    }

    @Test
    void decimalesConComa() {
        assertEquals("3,4", Dinero.decimal(3.41));
        assertEquals("0,7", Dinero.decimal(0.7));
        assertEquals("41,3", Dinero.decimal(41.25));
        assertEquals("15", Dinero.decimal(15.0));
        assertEquals("1.234,5", Dinero.decimal(1234.5));
    }

    @Test
    void porcentajeConEspacio() {
        assertEquals("10 %", CalculadoraCotizacion.pct(10.0));
        assertEquals("5,5 %", CalculadoraCotizacion.pct(5.5));
    }
}
