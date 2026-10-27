package com.drivemaster.drivemaster.dto.comun;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Filtro desde/hasta de las tablas con fecha. Llegan como días (AAAA-MM-DD) en hora de Colombia y se vuelven el rango
 * [desde 00:00, hasta + 1 día 00:00): "hasta" incluye todo ese día. La aplicación corre en America/Bogota
 * (DrivemasterApplication), así que las fechas guardadas (LocalDateTime) ya están en esa hora.
 */
public record RangoFechas(LocalDateTime desde, LocalDateTime antesDe) {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    public static final RangoFechas SIN_RANGO = new RangoFechas(null, null);

    /** desde y hasta son opcionales; cualquiera de los dos puede faltar. */
    public static RangoFechas de(String desde, String hasta) {
        LocalDate d = dia(desde, "desde");
        LocalDate h = dia(hasta, "hasta");
        if (d != null && h != null && d.isAfter(h)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha «desde» no puede ser posterior a «hasta».");
        }
        return new RangoFechas(d == null ? null : d.atStartOfDay(), h == null ? null : h.plusDays(1).atStartOfDay());
    }

    /** Hoy en Colombia (para los accesos rápidos y las pruebas). */
    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public boolean vacio() {
        return desde == null && antesDe == null;
    }

    /** El criterio sobre el campo de fecha, o null si no hay rango. */
    public Criteria criterio(String campo) {
        if (vacio()) return null;
        Criteria c = Criteria.where(campo);
        if (desde != null) c = c.gte(desde);
        if (antesDe != null) c = c.lt(antesDe);
        return c;
    }

    /** Para filtrar en memoria: sin rango, todo entra; sin fecha, nada entra si hay rango. */
    public boolean contiene(LocalDateTime fecha) {
        if (vacio()) return true;
        if (fecha == null) return false;
        return (desde == null || !fecha.isBefore(desde)) && (antesDe == null || fecha.isBefore(antesDe));
    }

    private static LocalDate dia(String texto, String nombre) {
        if (texto == null || texto.isBlank()) return null;
        try {
            // Acepta también una fecha con hora (los alias fechaInicio/fechaFin de Movimientos la mandaban así)
            String t = texto.trim();
            return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha «" + nombre + "» va como AAAA-MM-DD.");
        }
    }
}
