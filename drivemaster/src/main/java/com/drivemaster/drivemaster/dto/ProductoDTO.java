package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Compatibilidad;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Producto tal como lo ve el agente del chat (herramienta /api/productos/buscar).
 * Sin datos internos (costo, proveedor, ubicación ni stock mínimo). Los campos vacíos no se envían:
 * todo lo que va en la respuesta gasta tokens del modelo.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    /** STOCK | ENCARGO: un producto por encargo con stock 0 no está agotado. */
    private String tipo;
    private Integer diasEntregaEncargo;
    private Boolean compatibilidadUniversal;
    private List<Compatibilidad> modelosCompatibles;
    /** Nombre de la tienda que lo vende. */
    private String tienda;
}
