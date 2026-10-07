package com.drivemaster.drivemaster.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Festivos de Colombia (Ley 51 de 1983, "Ley Emiliani"): fijos, trasladados al lunes
 * siguiente y los que dependen de la Semana Santa. Se calculan por año, sin tablas
 * ni dependencias.
 */
public final class FestivosColombia {

    private static final Map<Integer, Set<LocalDate>> CACHE = new ConcurrentHashMap<>();

    private FestivosColombia() {
    }

    public static boolean esFestivo(LocalDate fecha) {
        return CACHE.computeIfAbsent(fecha.getYear(), FestivosColombia::calcular).contains(fecha);
    }

    private static Set<LocalDate> calcular(int anio) {
        Set<LocalDate> f = new HashSet<>();
        // Fijos
        f.add(LocalDate.of(anio, 1, 1));    // Año Nuevo
        f.add(LocalDate.of(anio, 5, 1));    // Día del Trabajo
        f.add(LocalDate.of(anio, 7, 20));   // Independencia
        f.add(LocalDate.of(anio, 8, 7));    // Batalla de Boyacá
        f.add(LocalDate.of(anio, 12, 8));   // Inmaculada Concepción
        f.add(LocalDate.of(anio, 12, 25));  // Navidad
        // Trasladados al lunes siguiente
        f.add(lunes(LocalDate.of(anio, 1, 6)));    // Reyes Magos
        f.add(lunes(LocalDate.of(anio, 3, 19)));   // San José
        f.add(lunes(LocalDate.of(anio, 6, 29)));   // San Pedro y San Pablo
        f.add(lunes(LocalDate.of(anio, 8, 15)));   // Asunción
        f.add(lunes(LocalDate.of(anio, 10, 12)));  // Día de la Raza
        f.add(lunes(LocalDate.of(anio, 11, 1)));   // Todos los Santos
        f.add(lunes(LocalDate.of(anio, 11, 11)));  // Independencia de Cartagena
        // Semana Santa
        LocalDate pascua = domingoDePascua(anio);
        f.add(pascua.minusDays(3));                 // Jueves Santo
        f.add(pascua.minusDays(2));                 // Viernes Santo
        f.add(lunes(pascua.plusDays(39)));          // Ascensión
        f.add(lunes(pascua.plusDays(60)));          // Corpus Christi
        f.add(lunes(pascua.plusDays(68)));          // Sagrado Corazón
        return f;
    }

    private static LocalDate lunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Algoritmo de Meeus/Jones/Butcher (calendario gregoriano). */
    static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int g = (8 * b + 13) / 25;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
