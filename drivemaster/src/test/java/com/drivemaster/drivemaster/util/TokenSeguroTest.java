package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenSeguroTest {

    @Test
    void generaTokenConFormatoValidoYHashDistinto() {
        TokenSeguro.Generado g = TokenSeguro.generar();
        assertEquals(43, g.token().length());
        assertTrue(TokenSeguro.formatoValido(g.token()));
        assertNotEquals(g.token(), g.hash());
        assertEquals(64, g.hash().length());
        assertEquals(TokenSeguro.hash(g.token()), g.hash());
    }

    @Test
    void cadaTokenEsDistinto() {
        assertNotEquals(TokenSeguro.generar().token(), TokenSeguro.generar().token());
    }

    @Test
    void rechazaFormatosInvalidos() {
        assertFalse(TokenSeguro.formatoValido(null));
        assertFalse(TokenSeguro.formatoValido(""));
        assertFalse(TokenSeguro.formatoValido("corto"));
        assertFalse(TokenSeguro.formatoValido("a".repeat(42) + "="));
        assertFalse(TokenSeguro.formatoValido("a".repeat(44)));
    }
}
