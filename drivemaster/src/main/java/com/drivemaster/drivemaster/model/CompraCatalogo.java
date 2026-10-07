package com.drivemaster.drivemaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compra directa del catálogo marketplace: el carrito de un cliente con repuestos de
 * una o varias tiendas, pagado con un solo pago de Wompi. Al confirmarse el pago se
 * crea un PedidoTienda por tienda (la tienda gestiona el suyo) y el reparto.
 *
 * Los precios, el IVA y el costo de envío se calculan siempre en el backend a partir
 * del producto y la tienda; lo que envía el navegador solo dice QUÉ y CUÁNTO.
 * No pasa por ningún taller.
 */
@Document(collection = "compras_catalogo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraCatalogo {

    // Esperando el pago de Wompi
    public static final String PENDIENTE_PAGO = "PENDIENTE_PAGO";
    // Pago aplicado: stock descontado y pedidos creados
    public static final String PAGADA = "PAGADA";
    // El pago se recibió pero no se pudo aplicar (ej. se agotó un repuesto): todo o nada, con reembolso pendiente
    public static final String NO_APLICADA = "NO_APLICADA";

    @Id
    private String id;

    private String clienteId;
    private String clienteNombre;
    private String clienteCorreo;

    @Builder.Default
    private List<Linea> lineas = new ArrayList<>();
    // Una por tienda: cómo se entrega y cuánto cuesta
    @Builder.Default
    private List<EntregaTienda> entregas = new ArrayList<>();

    // Dirección de envío (solo si alguna tienda va con ENVIO); el mismo destino para todas
    private String envioReceptor;
    private String envioTelefono;
    private String envioDireccion;
    private String envioCiudad;
    private String envioIndicaciones;

    private Double subtotal;
    private Double iva;
    private Double costoEnvio;
    private Double total;

    private Long pagoId;
    private String referenciaPago;

    private String estado;
    private String motivoNoAplicada;
    @Builder.Default
    private List<String> pedidoIds = new ArrayList<>();

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPago;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Linea {
        private String productoId;
        private String productoNombre;
        private String marca;
        private String referencia;
        private String imagenUrl;
        private String tiendaId;
        private String tiendaNombre;
        private int cantidad;
        private Double precioUnitario;
        private Double subtotal;
        // STOCK (se descuenta al pagar) o ENCARGO (la tienda lo consigue)
        private String tipo;
        private Integer diasEntrega;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntregaTienda {
        private String tiendaId;
        private String tiendaNombre;
        // ENVIO o RETIRO (PedidoTienda.ENVIO / PedidoTienda.RETIRO)
        private String tipo;
        private Double subtotal;
        private Double iva;
        // 0 en retiro; el costo fijo de la tienda en envío
        private Double costoEnvio;
        private Double total;
    }
}
