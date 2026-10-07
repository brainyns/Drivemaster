package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlazoHabilTest {

    private static TimeZone zonaOriginal;

    // Igual que en producción: la aplicación fija la zona de Colombia al arrancar.
    @BeforeAll
    static void zonaColombia() {
        zonaOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
    }

    @AfterAll
    static void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    // 2026-10-06 es martes.
    @Test
    void dentroDelHorarioSumaDirecto() {
        LocalDateTime r = PlazoHabil.sumarMinutosHabiles(LocalDateTime.of(2026, 10, 6, 9, 0), 120, 7, 19);
        assertEquals(LocalDateTime.of(2026, 10, 6, 11, 0), r);
    }

    @Test
    void elSobranteSePasaAlDiaSiguiente() {
        // 18:00 + 120 min hábiles = 60 min hoy + 60 min desde las 7:00 de mañana
        LocalDateTime r = PlazoHabil.sumarMinutosHabiles(LocalDateTime.of(2026, 10, 6, 18, 0), 120, 7, 19);
        assertEquals(LocalDateTime.of(2026, 10, 7, 8, 0), r);
    }

    @Test
    void deNocheEmpiezaALaApertura() {
        LocalDateTime r = PlazoHabil.sumarMinutosHabiles(LocalDateTime.of(2026, 10, 6, 22, 30), 120, 7, 19);
        assertEquals(LocalDateTime.of(2026, 10, 7, 9, 0), r);
    }

    @Test
    void madrugadaEmpiezaALaAperturaDelMismoDia() {
        LocalDateTime r = PlazoHabil.sumarMinutosHabiles(LocalDateTime.of(2026, 10, 7, 3, 0), 30, 7, 19);
        assertEquals(LocalDateTime.of(2026, 10, 7, 7, 30), r);
    }

    @Test
    void elDomingoNoCuenta() {
        // Sábado 10-oct 18:30 + 120 min = 30 min el sábado + 90 min el lunes 12-oct
        LocalDateTime r = PlazoHabil.sumarMinutosHabiles(LocalDateTime.of(2026, 10, 10, 18, 30), 120, 7, 19);
        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 30), r);
    }

    @Test
    void horarioInvalidoUsaPlazoCorrido() {
        LocalDateTime r = PlazoHabil.sumarMinutosHabiles(LocalDateTime.of(2026, 10, 6, 22, 0), 120, 19, 7);
        assertEquals(LocalDateTime.of(2026, 10, 7, 0, 0), r);
    }
}
