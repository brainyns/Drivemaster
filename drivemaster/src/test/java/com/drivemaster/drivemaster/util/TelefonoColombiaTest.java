package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class TelefonoColombiaTest {

    @Test
    void aceptaCelularesYFijosConFormatoLibre() {
        assertEquals("3001234567", TelefonoColombia.normalizar("300 123 4567"));
        assertEquals("3001234567", TelefonoColombia.normalizar("+57 300-123-4567"));
        assertEquals("3001234567", TelefonoColombia.normalizar("573001234567"));
        assertEquals("6012345678", TelefonoColombia.normalizar("(601) 234 5678"));
        assertEquals("6045551234", TelefonoColombia.normalizar("604.555.1234"));
    }

    @Test
    void rechazaLoQueNoEsUnNumeroColombianoVigente() {
        assertNull(TelefonoColombia.normalizar(null));
        assertNull(TelefonoColombia.normalizar(""));
        assertNull(TelefonoColombia.normalizar("2345678"), "fijo viejo de 7 dígitos");
        assertNull(TelefonoColombia.normalizar("200 123 4567"), "no empieza por 3 ni por 60");
        assertNull(TelefonoColombia.normalizar("609 123 4567"), "el indicativo 609 no existe");
        assertNull(TelefonoColombia.normalizar("300 123 456"), "9 dígitos");
        assertNull(TelefonoColombia.normalizar("300 123 45678"), "11 dígitos");
        assertNull(TelefonoColombia.normalizar("300abc4567"));
    }

    @Test
    void validarRespondeCuatrocientosConElMensaje() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> TelefonoColombia.validar("123"));
        assertEquals(400, e.getStatusCode().value());
        assertEquals(TelefonoColombia.MENSAJE, e.getReason());
    }
}
