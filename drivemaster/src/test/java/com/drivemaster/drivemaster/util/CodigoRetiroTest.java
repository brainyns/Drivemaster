package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodigoRetiroTest {

    @Test
    void generaCodigosDe8CaracteresSinLetrasConfundibles() {
        for (int i = 0; i < 500; i++) {
            String codigo = CodigoRetiro.generar();
            assertTrue(codigo.matches("[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}"), codigo);
            assertNotNull(CodigoRetiro.normalizar(codigo));
        }
    }

    @Test
    void sonAleatoriosYNoSeRepiten() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 2000; i++) vistos.add(CodigoRetiro.generar());
        assertEquals(2000, vistos.size(), "40 bits: 2000 códigos no deben chocar");
    }

    @Test
    void normalizaLoQueEscribeUnaPersonaEnElMostrador() {
        assertEquals("ABCD1234", CodigoRetiro.normalizar("abcd-1234"));
        assertEquals("ABCD1234", CodigoRetiro.normalizar(" ABCD 1234 "));
        // O→0 e I/L→1
        assertEquals("0000" + "1111", CodigoRetiro.normalizar("OOOO-IlLi"));
    }

    @Test
    void rechazaLoQueNoTieneFormaDeCodigo() {
        assertNull(CodigoRetiro.normalizar(null));
        assertNull(CodigoRetiro.normalizar(""));
        assertNull(CodigoRetiro.normalizar("ABC"));
        assertNull(CodigoRetiro.normalizar("ABCD-12345"));
        assertNull(CodigoRetiro.normalizar("ABCD-12U4")); // U no existe en el alfabeto
        assertNull(CodigoRetiro.normalizar("ABCD-12@4"));
    }

    @Test
    void elHashEstaLigadoAlPedidoYNoContieneElCodigo() {
        String codigo = "ABCD-1234";
        String h1 = CodigoRetiro.hash("pedido1", codigo);
        assertNotEquals(h1, CodigoRetiro.hash("pedido2", codigo), "el código de un pedido no sirve en otro");
        assertFalse(h1.contains("ABCD1234"));
        assertEquals(64, h1.length());
        assertEquals(h1, CodigoRetiro.hash("pedido1", "abcd 1234"), "es igual aunque se escriba distinto");
    }

    @Test
    void coincideSoloConElCodigoCorrectoDeEsePedido() {
        String codigo = CodigoRetiro.generar();
        String hash = CodigoRetiro.hash("p1", codigo);
        assertTrue(CodigoRetiro.coincide(hash, "p1", codigo));
        assertTrue(CodigoRetiro.coincide(hash, "p1", codigo.toLowerCase().replace("-", " ")));
        assertFalse(CodigoRetiro.coincide(hash, "p2", codigo), "pedido distinto");
        assertFalse(CodigoRetiro.coincide(hash, "p1", "0000-0000"));
        assertFalse(CodigoRetiro.coincide(hash, "p1", null));
        assertFalse(CodigoRetiro.coincide(hash, "p1", "basura"));
        assertFalse(CodigoRetiro.coincide(null, "p1", codigo));
    }
}
