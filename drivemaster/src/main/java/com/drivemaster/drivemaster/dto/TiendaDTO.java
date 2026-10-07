package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Vista de una tienda para su dueño y para el ADMIN (incluye datos administrativos). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendaDTO {
    private String id;
    private String nombre;
    private String ciudad;
    private String barrio;
    private String direccion;
    private Double latitud;
    private Double longitud;

    private String nit;
    private String telefono;
    private String correoContacto;
    private String correoUsuario;

    private String estadoAprobacion;
    private String motivoRechazo;
    private Boolean activo;
    private Boolean plataforma;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaAprobacion;

    // Suscripción vigente
    private String planActual;
    private LocalDateTime planVence;
}
