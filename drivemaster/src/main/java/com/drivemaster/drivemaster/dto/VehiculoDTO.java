package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehiculoDTO {
    private String id;
    private String marca;
    private String imagenUrl;
    private String modelo;
    private Integer anio;
    private String motor;
    private String combustible;
    private String placa;
    private Integer kilometraje;

    // Mantenimiento preventivo
    private Integer kmUltimoMantenimiento;
    private Integer intervaloMantenimientoKm;
    private Boolean recordatoriosActivos;
    private Integer kmProximoMantenimiento;
    private Boolean mantenimientoVencido;
    /** Última vez que se envió el aviso de mantenimiento, para mostrarlo al cliente. */
    private LocalDateTime ultimoRecordatorioEnviado;
}