package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class MotivosTest {

    @Test
    void exigeDiezCaracteresSinContarLosEspacios() {
        for (String corto : new String[] { null, "", "   ", "no vino", "  123456789  " }) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> Motivos.obligatorio(corto, "cancelas la compra", 300));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertEquals("Explica en al menos 10 caracteres por qué cancelas la compra", e.getReason());
        }
        assertEquals("1234567890", Motivos.obligatorio("  1234567890 ", "x", 300));
    }

    @Test
    void recortaAlMaximo() {
        assertEquals(20, Motivos.obligatorio("a".repeat(50), "x", 20).length());
    }
}
