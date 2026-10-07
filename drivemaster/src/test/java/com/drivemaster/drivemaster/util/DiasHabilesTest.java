package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Días hábiles (lunes a viernes sin festivos de Colombia) para el plazo de despacho. */
class DiasHabilesTest {

    /** Hora de Bogotá expresada en la zona del servidor, como la reciben los servicios. */
    private static LocalDateTime enServidor(int anio, int mes, int dia, int hora) {
        return ZonedDateTime.of(anio, mes, dia, hora, 0, 0, 0, PlazoHabil.ZONA_COLOMBIA)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static LocalDate enBogota(LocalDateTime servidor) {
        return servidor.atZone(ZoneId.systemDefault()).withZoneSameInstant(PlazoHabil.ZONA_COLOMBIA).toLocalDate();
    }

    @Test
    void pascuaDe2026Es5DeAbril() {
        assertEquals(LocalDate.of(2026, 4, 5), FestivosColombia.domingoDePascua(2026));
        assertEquals(LocalDate.of(2025, 4, 20), FestivosColombia.domingoDePascua(2025));
    }

    @Test
    void festivosDe2026SegunElCalendarioOficial() {
        List<LocalDate> oficiales = List.of(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
        for (LocalDate f : oficiales) assertTrue(FestivosColombia.esFestivo(f), f + " debe ser festivo");
        long festivos = LocalDate.of(2026, 1, 1).datesUntil(LocalDate.of(2027, 1, 1))
                .filter(FestivosColombia::esFestivo).count();
        assertEquals(oficiales.size(), festivos, "no debe haber festivos de más");
    }

    @Test
    void sumaDiasHabilesSaltandoFinDeSemana() {
        // Viernes 9-oct-2026 + 2 días hábiles: el lunes 12 es festivo -> martes 13 (1), miércoles 14 (2)
        LocalDateTime r = PlazoHabil.sumarDiasHabiles(enServidor(2026, 10, 9, 12), 2);
        assertEquals(LocalDate.of(2026, 10, 14), enBogota(r));
    }

    @Test
    void sumaDiasHabilesSaltandoSemanaSanta() {
        // Miércoles 1-abr-2026 + 2: jueves 2 y viernes 3 son festivos -> martes 7
        LocalDateTime r = PlazoHabil.sumarDiasHabiles(enServidor(2026, 4, 1, 12), 2);
        assertEquals(LocalDate.of(2026, 4, 7), enBogota(r));
    }

    @Test
    void unaCompraEnDiaFestivoCuentaDesdeElSiguienteHabil() {
        // Domingo 11-oct-2026 + 2: el lunes 12 es festivo -> martes 13 (1), miércoles 14 (2)
        LocalDateTime r = PlazoHabil.sumarDiasHabiles(enServidor(2026, 10, 11, 12), 2);
        assertEquals(LocalDate.of(2026, 10, 14), enBogota(r));
    }

    @Test
    void conceroDiasNoCambiaLaFecha() {
        LocalDateTime t = enServidor(2026, 10, 7, 9);
        assertEquals(t, PlazoHabil.sumarDiasHabiles(t, 0));
    }

    @Test
    void conservaLaHoraDelPago() {
        LocalDateTime t = enServidor(2026, 10, 7, 15);
        LocalDateTime r = PlazoHabil.sumarDiasHabiles(t, 2);
        assertEquals(t.toLocalTime(), r.toLocalTime());
    }

    @Test
    void sabadoYDomingoNoSonHabiles() {
        assertFalse(PlazoHabil.esDiaHabil(LocalDate.of(2026, 10, 10)));
        assertFalse(PlazoHabil.esDiaHabil(LocalDate.of(2026, 10, 11)));
        assertTrue(PlazoHabil.esDiaHabil(LocalDate.of(2026, 10, 13)));
    }
}
