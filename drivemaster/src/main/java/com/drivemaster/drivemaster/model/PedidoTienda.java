package com.drivemaster.drivemaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repuestos que una tienda debe llevar al taller, creado al confirmarse el pago de
 * una cotización (uno por tienda). Va aparte de "ventas" para no mezclar el
 * marketplace con los reportes de la tienda propia.
 *
 * No lleva datos del cliente: la tienda solo necesita saber qué y a qué taller.
 */
@Document(collection = "pedidos_tienda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoTienda {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String DESPACHADO = "DESPACHADO";
    public static final String NO_DESPACHADO = "NO_DESPACHADO";
    public static final String RECIBIDO = "RECIBIDO";

    @Id
    private String id;

    private String ordenId;
    private String cotizacionId;
    private Long pagoId;

    private String tiendaId;
    private String tiendaNombre;

    // Destino de la entrega
    private String tallerId;
    private String tallerNombre;
    private String tallerDireccion;
    private String tallerCiudad;
    private String tallerTelefono;
    private String vehiculoDescripcion;

    private List<CotizacionItem> items;
    private Double subtotal;
    private Double iva;
    private Double total;
    // Días máximos de entrega de los ítems por encargo (null si todo estaba en stock)
    private Integer diasEntrega;

    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaDespacho;
    private String notaDespacho;
    private LocalDateTime fechaNoDespacho;
    private String motivoNoDespacho;
    private LocalDateTime fechaRecibido;
}
