package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Taller;

import java.util.List;

/**
 * Datos públicos de un taller para elegirlo en la lista y el mapa del cliente: identificación, ubicación, contacto
 * del local, horario, servicios, calificación (promedio y cuántas; null y 0 sin calificaciones) y logo. Nada de la cuenta
 * ni de la operación interna (dueño, correo, mecánicos, tarifas, estado de revisión, plan).
 */
public record TallerMapaDTO(
        String id,
        String nombre,
        String direccion,
        String ciudad,
        String telefono,
        Double latitud,
        Double longitud,
        String horario,
        List<String> especialidades,
        Double calificacion,
        int calificaciones,
        String logoUrl) {

    /** Solo ubicación y contacto (cálculos de cercanía y pruebas). */
    public TallerMapaDTO(String id, String nombre, String direccion, String ciudad, String telefono,
                         Double latitud, Double longitud) {
        this(id, nombre, direccion, ciudad, telefono, latitud, longitud, null, List.of(), null, 0, null);
    }

    public static TallerMapaDTO de(Taller t) {
        return new TallerMapaDTO(t.getId(), t.getNombre(), t.getDireccion(), t.getCiudad(), t.getTelefono(),
                t.getLatitud(), t.getLongitud(), t.getHorario(),
                t.getEspecialidades() != null ? List.copyOf(t.getEspecialidades()) : List.of(),
                t.getRating(), t.getCalificaciones() != null ? t.getCalificaciones() : 0, t.getLogoUrl());
    }
}
