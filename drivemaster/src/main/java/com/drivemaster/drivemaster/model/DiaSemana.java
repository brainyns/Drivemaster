package com.drivemaster.drivemaster.model;

import java.time.DayOfWeek;

/** Día del horario de atención del taller, en el orden en que se muestra (lunes primero). */
public enum DiaSemana {
    LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO, DOMINGO;

    public static DiaSemana de(DayOfWeek dia) {
        return values()[dia.getValue() - 1];
    }

    /** Nombre corto para el resumen en texto que ve el cliente. */
    public String corto() {
        return switch (this) {
            case LUNES -> "Lun";
            case MARTES -> "Mar";
            case MIERCOLES -> "Mié";
            case JUEVES -> "Jue";
            case VIERNES -> "Vie";
            case SABADO -> "Sáb";
            case DOMINGO -> "Dom";
        };
    }
}
