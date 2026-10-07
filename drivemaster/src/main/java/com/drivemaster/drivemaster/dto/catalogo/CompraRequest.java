package com.drivemaster.drivemaster.dto.catalogo;

import java.util.List;

/**
 * Carrito que el cliente quiere cotizar o pagar. Solo dice QUÉ y CUÁNTO y cómo recibir
 * de cada tienda: los precios, el IVA y el costo de envío los calcula el backend.
 */
public record CompraRequest(List<Item> items, List<Entrega> entregas, Envio envio, String vehiculoId) {

    public record Item(String productoId, Integer cantidad) {
    }

    /** tipo: ENVIO o RETIRO. Una por cada tienda del carrito. */
    public record Entrega(String tiendaId, String tipo) {
    }

    /** Obligatorio si alguna tienda va con ENVIO. */
    public record Envio(String receptor, String telefono, String direccion, String ciudad, String indicaciones) {
    }
}
