package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    private String id;

    private String codigo;
    private String nombre;
    private String categoria;
    private String marca;

    private List<Compatibilidad> modelosCompatibles = new ArrayList<>();

    private Double precioCompra;
    private Double precioVenta;
    private Integer stockActual;
    private Integer stockMinimo;

    private String tipo; // STOCK | ENCARGO
    private Boolean activo;
    private String imagenUrl;
}

