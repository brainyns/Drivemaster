package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Búsqueda del chat: el cliente escribe como habla y el catálogo dice otra cosa. */
class PatronBusquedaTest {

    /** Igual que Mongo con la opción 'i': búsqueda "contiene", sin distinguir mayúsculas. */
    private static boolean encuentra(String termino, String textoDelCatalogo) {
        return Pattern.compile(PatronBusqueda.aRegex(termino), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(textoDelCatalogo).find();
    }

    @Test
    void rodamientoEncuentraLaBalinera() {
        assertTrue(encuentra("rodamientos", "Balinera delantera Ford Escape"));
        assertTrue(encuentra("rodamiento", "Balinera delantera Ford Escape"));
        assertTrue(encuentra("balineras", "Rodamiento de rueda trasera"));
        assertTrue(encuentra("cojinete", "Balinera delantera Ford Escape"));
    }

    @Test
    void daIgualLaTildeEnElTerminoOEnElCatalogo() {
        assertTrue(encuentra("bateria", "Batería 12V 45Ah"));
        assertTrue(encuentra("batería", "BATERIA 12V"));
        assertTrue(encuentra("bujia", "Bujía de iridio"));
        assertTrue(encuentra("acumulador", "Batería 12V"), "sinónimo con tilde en el catálogo");
    }

    @Test
    void daIgualElPlural() {
        assertTrue(encuentra("frenos", "Cilindro freno trasero Chevrolet Aveo"));
        assertTrue(encuentra("motores", "Motor de arranque"));
        assertTrue(encuentra("aceites", "Aceite 20W50"));
        assertTrue(encuentra("pastillas", "Pastilla de freno Bosch"));
        assertTrue(encuentra("aceites", "Lubricante sintético 5W30"), "el plural también trae los sinónimos");
    }

    @Test
    void noInventaCoincidencias() {
        assertFalse(encuentra("rodamiento", "Cilindro freno trasero Chevrolet Aveo"));
        assertFalse(encuentra("bateria", "Balinera delantera Ford Escape"));
    }

    @Test
    void losCaracteresEspecialesSonTextoNoExpresiones() {
        assertEquals("c\\+\\+", PatronBusqueda.aRegex("c++"));
        assertTrue(encuentra("1.6", "Motor 1.6"));
        assertFalse(encuentra("1.6", "Motor 106"), "el punto es un punto, no 'cualquier carácter'");
        assertDoesNotThrow(() -> Pattern.compile(PatronBusqueda.aRegex("(aceite]")));
    }

    @Test
    void lasPalabrasCortasNoSePierden() {
        assertEquals("g[aáÁ]s", PatronBusqueda.aRegex("gas"));
        assertTrue(encuentra("abs", "Sensor ABS delantero"));
    }
}
