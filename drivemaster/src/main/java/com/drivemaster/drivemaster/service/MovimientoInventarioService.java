package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.util.List;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;

public interface MovimientoInventarioService {

        MovimientoInventario registrarMovimiento(
                        String productoId,
                        String tipo,
                        Integer cantidad,
                        String motivo,
                        String referencia,
                        String usuarioId);

        MovimientoInventario registrarNuevoProducto(Producto producto, String usuarioId);

        List<MovimientoInventario> obtenerMovimientosPorProducto(String productoId);

        List<MovimientoInventario> listarTodos();

        List<MovimientoInventario> buscarConFiltros(
                        String productoId,
                        String tipo,
                        LocalDateTime fechaInicio,
                        LocalDateTime fechaFin);
}
