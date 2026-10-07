package com.drivemaster.drivemaster.model;

import lombok.*;

/**
 * Línea de una cotización: un repuesto (con la tienda que lo vende y el precio
 * congelado al emitir) o un concepto de mano de obra (por ítem o por horas).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionItem {

    public static final String REPUESTO = "REPUESTO";
    public static final String MANO_OBRA = "MANO_OBRA";
    public static final String POR_ITEM = "ITEM";
    public static final String POR_HORAS = "HORAS";

    private String tipo;
    private String descripcion;

    // Repuesto
    private String productoId;
    private String productoNombre;
    private String marca;
    private String referencia;
    private String tiendaId;
    private String tiendaNombre;
    private String disponibilidad;
    private Integer diasEntrega;

    // Mano de obra: ITEM (valor fijo) o HORAS (horas × tarifa por hora del taller)
    private String modalidad;
    private Double horas;

    private Integer cantidad;
    // Antes de IVA
    private Double precioUnitario;
    private Double subtotal;
}
