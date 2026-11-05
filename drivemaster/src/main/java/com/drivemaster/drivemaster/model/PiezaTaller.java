package com.drivemaster.drivemaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una pieza en el inventario de un taller, con sus lotes (uno por pedido recibido). Es independiente de Producto: el
 * stock de aquí es del taller y venderlo nunca toca el de una tienda.
 *
 * El stock y los lotes viven en un mismo documento y version evita que dos ventas se pisen: al guardar, el que llegó
 * tarde recibe un error y repite con los datos nuevos (ver StockTallerService).
 *
 * El id es tallerId|clave: Mongo no deja crear la misma pieza dos veces aunque lleguen dos entradas a la vez.
 * El costo y el margen nunca salen de aquí hacia el cliente: esta clase no se serializa, solo sus DTO del taller.
 */
@Document(collection = "piezas_taller")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PiezaTaller {

    // Recargo sobre el costo que se propone como precio de venta; el taller lo cambia
    public static final double RECARGO_SUGERIDO_PCT = 35;

    @Id
    private String id;
    @Version
    private Long version;

    private String tallerId;
    // Misma regla que agrupa las ofertas: referencia o, sin ella, marca + nombre (BuscadorRepuestosService.clavePieza)
    private String clave;

    // Copia del producto de la tienda al recibirlo
    private String nombre;
    private String marca;
    private String referencia;
    private String categoria;
    private String imagenUrl;
    @Builder.Default
    private List<Compatibilidad> modelosCompatibles = new ArrayList<>();
    private Boolean compatibilidadUniversal;

    // Antes de IVA, como todos los precios de una cotización. Lo define el taller
    private Double precioVenta;
    // Suma de lo disponible en los lotes
    private int stock;
    // Lo define el taller; null = sin alerta. Con stock <= mínimo se avisa una vez (avisoStockBajo) hasta que vuelva a subir
    private Integer stockMinimo;
    private Boolean avisoStockBajo;
    // Los lotes agotados se conservan: son el rastro del costo y a donde vuelve una devolución
    @Builder.Default
    private List<Lote> lotes = new ArrayList<>();

    private LocalDateTime fechaCreacion;

    public static String idDe(String tallerId, String clave) {
        return tallerId + "|" + clave;
    }

    /** Lo que vino en un pedido recibido. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Lote {
        // pedidoId|productoId: un mismo pedido no entra dos veces
        private String id;
        private String compraId;
        private String pedidoId;
        private String productoId;
        private String tiendaId;
        private String tiendaNombre;
        private int cantidad;
        private int disponible;
        // Sin IVA si el taller es responsable (lo descuenta); con IVA si no lo es (es parte de su costo)
        private Double costoUnitario;
        // Congelados al comprar: el precio público de la tienda, su descuento para talleres y lo que se pagó por unidad
        // (antes de IVA). Si la tienda cambia después, el costo de este lote no cambia
        private Double precioPublico;
        private Double descuentoPct;
        private Double precioPagado;
        private Boolean ivaEnCosto;
        private LocalDateTime fechaEntrada;
    }
}
