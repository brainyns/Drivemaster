package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearVehiculoRequest {
    private String marca;
    private String modelo;
    private Integer anio;
    private String motor;
    private String combustible;
    private String placa;
    private Integer kilometraje;
}