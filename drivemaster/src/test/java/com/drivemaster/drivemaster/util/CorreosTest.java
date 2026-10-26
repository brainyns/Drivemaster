package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorreosTest {

    @Test
    void normalizaAMinusculasYSinEspacios() {
        assertEquals("ana.perez@gmail.com", Correos.normalizar("  Ana.Perez@Gmail.COM "));
        assertNull(Correos.normalizar(null));
    }

    @Test
    void validaLaFormaBasica() {
        assertTrue(Correos.valido("taller@demo.co"));
        assertFalse(Correos.valido("taller@demo"));
        assertFalse(Correos.valido("taller demo@x.co"));
        assertFalse(Correos.valido(null));
        assertFalse(Correos.valido("a@b.c" + "x".repeat(260)));
    }
}
