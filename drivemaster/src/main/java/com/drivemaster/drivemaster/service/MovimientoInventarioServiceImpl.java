package com.drivemaster.drivemaster.service;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MovimientoInventarioServiceImpl implements MovimientoInventarioService {

    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoService productoService;

    public MovimientoInventarioServiceImpl(MovimientoInventarioRepository movimientoRepository,
            ProductoService productoService) {
        this.movimientoRepository = movimientoRepository;
        this.productoService = productoService;
    }

    @Override
    public MovimientoInventario registrarMovimiento(
            String productoId,
            String tipo,
            Integer cantidad,
            String motivo,
            String referencia,
            String usuarioId) {

        Producto producto = productoService.obtenerPorId(productoId);

        int stockAnterior = producto.getStockActual();
        int stockNuevo;

        switch (tipo) {
            case "ENTRADA":
                stockNuevo = stockAnterior + cantidad;
                break;

            case "SALIDA":
                stockNuevo = stockAnterior - cantidad;
                if (stockNuevo < 0) {
                    throw new RuntimeException("Stock insuficiente");
                }
                break;

            case "AJUSTE":
                stockNuevo = cantidad;
                break;

            default:
                throw new RuntimeException("Tipo de movimiento inválido");
        }

        // Actualizar stock del producto
        productoService.actualizarStock(productoId, stockNuevo);

        // Crear movimiento
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProductoId(productoId);
        movimiento.setTipo(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setStockAnterior(stockAnterior);
        movimiento.setStockNuevo(stockNuevo);
        movimiento.setMotivo(motivo);
        movimiento.setReferencia(referencia);
        movimiento.setUsuarioId(usuarioId);
        movimiento.setFecha(LocalDateTime.now());

        return movimientoRepository.save(movimiento);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorProducto(String productoId) {

        return movimientoRepository
                .findByProductoIdOrderByFechaDesc(productoId);
    }

    @Override
    public List<MovimientoInventario> listarTodos() {
        return movimientoRepository.findAllByOrderByFechaDesc();
    }

    @Override
    public List<MovimientoInventario> buscarConFiltros(String productoId, String tipo, LocalDateTime fechaInicio,
            LocalDateTime fechaFin) {
        List<MovimientoInventario> movimientos;

        // Caso 1: todos sin filtros
        movimientos = movimientoRepository.findAllByOrderByFechaDesc();

        // Filtro por producto
        if (productoId != null && !productoId.isBlank()) {
            movimientos = movimientos.stream()
                    .filter(m -> m.getProductoId().equals(productoId))
                    .toList();
        }

        // Filtro por tipo
        if (tipo != null && !tipo.isBlank()) {
            movimientos = movimientos.stream()
                    .filter(m -> m.getTipo().equals(tipo))
                    .toList();
        }

        // Filtro por rango de fechas
        if (fechaInicio != null && fechaFin != null) {
            movimientos = movimientos.stream()
                    .filter(m -> !m.getFecha().isBefore(fechaInicio) &&
                            !m.getFecha().isAfter(fechaFin))
                    .toList();
        }

        return movimientos;
    }

    @Override
    public MovimientoInventario registrarNuevoProducto(Producto producto, String usuarioId) {
        productoService.crearProducto(producto);
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProductoId(producto.getId());
        movimiento.setTipo("ENTRADA");
        movimiento.setCantidad(producto.getStockActual());
        movimiento.setStockAnterior(0);
        movimiento.setStockNuevo(producto.getStockActual());
        movimiento.setMotivo("Registro de nuevo producto");
        movimiento.setReferencia("PROD_" + producto.getId());
        movimiento.setUsuarioId(usuarioId);
        movimiento.setFecha(LocalDateTime.now());

        return movimientoRepository.save(movimiento);
    }

}
