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
    // De dónde sale el repuesto. Sin origen: de una tienda (todas las cotizaciones anteriores)
    public static final String ORIGEN_TALLER = "TALLER";
    public static final String ORIGEN_TIENDA = "TIENDA";

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
    // TALLER: sale del inventario del taller y no tiene tienda, productoId ni pedido. El costo nunca va aquí: el cliente ve estos ítems
    private String origen;
    private String piezaTallerId;
    // Compra de un taller en el catálogo: el precio público y el descuento con que se le vendió (precioUnitario es lo que
    // pagó). Nulos en cotizaciones y en compras de clientes
    private Double precioPublico;
    private Double descuentoPct;

    // Mano de obra: ITEM (valor fijo) o HORAS (horas × tarifa por hora del taller)
    private String modalidad;
    private Double horas;

    private Integer cantidad;
    // Antes de IVA
    private Double precioUnitario;
    private Double subtotal;

    /** Repuesto del inventario del taller: no toca el stock de ninguna tienda ni genera pedido. */
    public boolean esDelTaller() {
        return ORIGEN_TALLER.equals(origen);
    }
}
