package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Solicitud de diagnóstico que llena el cliente. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearSolicitudServicioRequest {

    private String vehiculoId;
    private String sintomas;

    // URLs devueltas por POST /api/solicitudes-servicio/fotos (máximo 5)
    private List<String> fotos;

    private String ciudad;
    private String barrio;
    private Double latitud;
    private Double longitud;

    // Texto libre: "Mañanas entre semana", "Sábado temprano"...
    private String preferenciaHorario;
}
