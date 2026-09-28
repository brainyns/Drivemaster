package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TallerDTO {
    private String id;
    private String nombre;
    private String ciudad;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private List<String> especialidades;
    private Double rating;
    private Double precioManoObra;
    private Boolean activo;

    // Solo se llena cuando se calcula contra una ubicación de referencia
    private Double distanciaKm;
}