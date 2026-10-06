package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearTallerRequest {
    // Datos del taller
    private String nombre;
    private String ciudad;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private List<String> especialidades;
    private Double rating;
    private Double precioManoObra;
    private String barrio;
    private String telefono;
    private Double tarifaHora;
    private String horario;

    // Datos del usuario asociado (rol TALLER). Si el taller ya tiene
    // usuarioId (edición), estos campos pueden omitirse.
    private String correoUsuario;
    private String passwordUsuario;
    private String usuarioId;
}