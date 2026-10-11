package com.drivemaster.drivemaster.dto;

/** Datos públicos de un taller para pintarlo en el mapa del cliente. */
public record TallerMapaDTO(
        String id,
        String nombre,
        String direccion,
        String ciudad,
        String telefono,
        Double latitud,
        Double longitud) {
}
