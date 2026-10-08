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

    // Cuándo ingresó al catálogo. Los anteriores no lo tienen: se deduce del _id de Mongo.
    private java.time.LocalDateTime fechaCreacion;
    private Boolean activo;
    private String imagenUrl;

    // ─── Marketplace ───
    // Tienda que vende el producto. Cada documento es la oferta de una tienda: el
    // stock y el precio son de esa tienda.
    private String tiendaId;

    // Referencia / número de parte del fabricante. Agrupa la misma pieza entre tiendas.
    private String referencia;

    // Ubicación física en la tienda (bodega, estante...).
    private String ubicacion;

    // Proveedor del que la tienda obtiene el producto (opcional).
    private String proveedorId;

    // Solo productos por ENCARGO: días estimados de entrega (se informan al cotizar).
    private Integer diasEntregaEncargo;

    // La tienda lo marca para aceites, líquidos e insumos que sirven a cualquier
    // vehículo: el buscador los muestra como compatibles sin pedir confirmación.
    private Boolean compatibilidadUniversal;
}

