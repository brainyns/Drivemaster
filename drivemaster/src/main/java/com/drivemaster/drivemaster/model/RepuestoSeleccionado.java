package com.drivemaster.drivemaster.model;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Repuesto que el mecánico eligió para la reparación: qué producto, de qué tienda
 * y cuántas unidades. Va embebido en la OrdenServicio. El precio es de
 * referencia; la cotización (Fase 3) lo congela al emitirse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepuestoSeleccionado {

    // Qué necesita el vehículo, en palabras del mecánico (ej. "Pastillas de freno delanteras")
    private String descripcion;
    private Integer cantidad;

    // Producto elegido (oferta de una tienda)
    private String productoId;
    private String productoNombre;
    private String marca;
    private String referencia;
    private String tiendaId;
    private String tiendaNombre;

    private Double precioReferencia;

    // DISPONIBLE o ENCARGO al momento de elegirlo
    private String disponibilidad;
    // Días estimados de entrega si es por encargo
    private Integer diasEntrega;
    private Double distanciaKm;

    // COMPATIBLE o POR_CONFIRMAR al momento de elegirlo
    private String compatibilidad;

    // Solo POR_CONFIRMAR: el mecánico confirmó que le sirve al vehículo
    private Boolean compatibilidadConfirmada;
    private String confirmadoPorMecanicoId;
    private String confirmadoPorMecanicoNombre;
    private LocalDateTime fechaConfirmacion;
}
