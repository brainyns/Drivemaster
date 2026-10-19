package com.drivemaster.drivemaster.dto;

/**
 * Taller cercano a un punto. distanciaKm es en línea recta (Haversine); distanciaRutaKm y duracionMin son en carro
 * (OSRM) y quedan en null si el servicio de rutas no respondió.
 */
public record TallerCercanoDTO(
        String id,
        String nombre,
        String direccion,
        String ciudad,
        String telefono,
        Double latitud,
        Double longitud,
        double distanciaKm,
        Double distanciaRutaKm,
        Double duracionMin) {
}
