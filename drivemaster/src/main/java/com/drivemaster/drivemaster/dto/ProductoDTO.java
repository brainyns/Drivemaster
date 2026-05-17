package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoDTO {
    private String id;
    private String codigo;
    private String nombre;
    private String categoria;
    private String marca;
    private Double precioVenta;
    private Integer stockActual;
    private String imagenUrl;
}