package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.Tienda;

/**
 * Las compras a proveedores son de cada tienda: se registran con la tienda dueña, solo con sus proveedores y sus
 * productos, y solo ella las ve. Los métodos "DeTienda" filtran por dueño en el servidor.
 */
public interface CompraService {
    Compra registrarCompra(Compra compra, Tienda tienda);
    Compra obtenerDeTienda(String id, String tiendaId);
    List<Compra> listarDeTienda(String tiendaId);
    byte[] generarPdfDeTienda(String id, String tiendaId);

    /** PENDIENTE → RECIBIDA: recién ahí se suma el stock (una sola vez). 409 si ya estaba recibida o cancelada. */
    Compra recibirCompra(String id, String tiendaId);

    /** PENDIENTE → CANCELADA, con motivo opcional. No toca el stock. 409 si ya estaba recibida o cancelada. */
    Compra cancelarCompra(String id, String tiendaId, String motivo);

    /** Sin filtro de dueño: solo para uso interno (reportes, migraciones). No se expone por la API. */
    Compra obtenerPorId(String id);
    List<Compra> listarTodas();
    byte[] generarPdf(String id);
}
