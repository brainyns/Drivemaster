package com.drivemaster.drivemaster.dto.catalogo;

import java.util.List;

/**
 * Producto del catálogo marketplace con su tienda. No expone precio de compra, proveedor,
 * ubicación en bodega ni nada administrativo.
 *
 * compatibilidad (solo si el cliente eligió un vehículo): COMPATIBLE, NO_COMPATIBLE o
 * POR_CONFIRMAR. Solo avisa; nunca oculta el producto.
 */
public record ProductoCatalogoDTO(String id, String nombre, String marca, String categoria, String referencia,
                                  String imagenUrl, boolean imagenIa, Double precio, String tipo, boolean disponible,
                                  Integer stock, Integer diasEntrega,
                                  String compatibilidad, String avisoCompatibilidad,
                                  TiendaCatalogoDTO tienda,
                                  // Solo en el detalle: el mismo repuesto en otras tiendas
                                  List<ProductoCatalogoDTO> otrasTiendas,
                                  // Solo para un taller aprobado: lo que él paga y el descuento de esa tienda. precio sigue siendo el público
                                  Double precioTaller, Double descuentoTallerPct) {

    public ProductoCatalogoDTO conOtrasTiendas(List<ProductoCatalogoDTO> otras) {
        return new ProductoCatalogoDTO(id, nombre, marca, categoria, referencia, imagenUrl, imagenIa, precio, tipo, disponible,
                stock, diasEntrega, compatibilidad, avisoCompatibilidad, tienda, otras, precioTaller, descuentoTallerPct);
    }

    /** El mismo producto visto por un taller aprobado: su precio con el descuento de SU tienda (cada tienda fija el suyo). */
    public ProductoCatalogoDTO paraTaller(double pct) {
        Double conDescuento = precio == null ? null : com.drivemaster.drivemaster.util.DescuentoTaller.precio(precio, pct);
        return new ProductoCatalogoDTO(id, nombre, marca, categoria, referencia, imagenUrl, imagenIa, precio, tipo, disponible,
                stock, diasEntrega, compatibilidad, avisoCompatibilidad, tienda, otrasTiendas, conDescuento, pct);
    }
}
