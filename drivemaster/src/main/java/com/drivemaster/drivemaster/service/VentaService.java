package com.drivemaster.drivemaster.service;

import java.util.List;
import com.drivemaster.drivemaster.dto.CartItemRequest;
import com.drivemaster.drivemaster.model.Venta;

public interface VentaService {

    Venta registrarVenta(Venta venta);

    Venta compraInmediata(String email, List<CartItemRequest> productos, String metodoPago);

    Venta confirmarPagoVenta(String ventaId, String referencia);

    Venta obtenerPorId(String id);

    List<Venta> listarTodas();

    List<Venta> listarPorUsuarioId(String usuarioId);

    void anularVenta(String ventaId);

    byte[] generarPdf(String id);

    byte[] exportarExcel();
}