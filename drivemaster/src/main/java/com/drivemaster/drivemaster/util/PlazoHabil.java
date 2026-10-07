package com.drivemaster.drivemaster.util;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Suma minutos contando solo el horario de atención de los talleres (lunes a
 * sábado, hora de Colombia). Así el plazo para responder una solicitud no se
 * consume de noche ni el domingo, cuando los talleres están cerrados.
 *
 * Entrada y salida van en la zona horaria del servidor (la que usa
 * LocalDateTime.now() en el resto del backend); el cálculo se hace en Bogotá.
 */
public final class PlazoHabil {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private PlazoHabil() {
    }

    public static LocalDateTime sumarMinutosHabiles(LocalDateTime desdeServidor, long minutos,
                                                    int horaInicio, int horaFin) {
        ZoneId zonaServidor = ZoneId.systemDefault();
        if (horaInicio < 0 || horaFin > 24 || horaFin <= horaInicio) {
            // Horario mal configurado: plazo corrido.
            return desdeServidor.plusMinutes(minutos);
        }

        ZonedDateTime t = desdeServidor.atZone(zonaServidor).withZoneSameInstant(ZONA_COLOMBIA);
        long restantes = minutos;

        while (true) {
            t = ajustarAlHorario(t, horaInicio, horaFin);
            ZonedDateTime cierre = t.truncatedTo(ChronoUnit.DAYS).plusHours(horaFin);
            long disponibles = ChronoUnit.MINUTES.between(t, cierre);
            if (restantes <= disponibles) {
                return t.plusMinutes(restantes).withZoneSameInstant(zonaServidor).toLocalDateTime();
            }
            restantes -= disponibles;
            t = cierre;
        }
    }

    /**
     * Suma días hábiles: de lunes a viernes, sin festivos de Colombia. El día de
     * partida no cuenta; conserva la hora. Con 0 días devuelve la misma fecha.
     */
    public static LocalDateTime sumarDiasHabiles(LocalDateTime desdeServidor, int dias) {
        if (dias <= 0) return desdeServidor;
        ZoneId zonaServidor = ZoneId.systemDefault();
        ZonedDateTime t = desdeServidor.atZone(zonaServidor).withZoneSameInstant(ZONA_COLOMBIA);
        int restantes = dias;
        while (restantes > 0) {
            t = t.plusDays(1);
            if (esDiaHabil(t.toLocalDate())) restantes--;
        }
        return t.withZoneSameInstant(zonaServidor).toLocalDateTime();
    }

    public static boolean esDiaHabil(java.time.LocalDate dia) {
        DayOfWeek d = dia.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !FestivosColombia.esFestivo(dia);
    }

    /** Si t cae fuera del horario, lo mueve a la próxima apertura. */
    private static ZonedDateTime ajustarAlHorario(ZonedDateTime t, int horaInicio, int horaFin) {
        ZonedDateTime dia = t.truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime apertura = dia.plusHours(horaInicio);
        ZonedDateTime cierre = dia.plusHours(horaFin);

        if (t.getDayOfWeek() != DayOfWeek.SUNDAY && !t.isBefore(apertura) && t.isBefore(cierre)) {
            return t;
        }
        ZonedDateTime siguiente = t.isBefore(apertura) && t.getDayOfWeek() != DayOfWeek.SUNDAY
                ? apertura
                : dia.plusDays(1).plusHours(horaInicio);
        while (siguiente.getDayOfWeek() == DayOfWeek.SUNDAY) {
            siguiente = siguiente.plusDays(1);
        }
        return siguiente;
    }
}
