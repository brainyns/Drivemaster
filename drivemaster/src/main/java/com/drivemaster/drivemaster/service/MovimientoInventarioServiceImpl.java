package com.drivemaster.drivemaster.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

        if (cantidad == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad es obligatoria");
        }
        // Una entrada negativa restaría y un ajuste negativo dejaría el stock bajo cero: se rechazan aquí, no solo en el formulario
        if (cantidad < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad no puede ser negativa");
        }
        if (cantidad == 0 && !"AJUSTE".equals(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que 0");
        }

        Producto producto;
        int stockAnterior;
        int stockNuevo;

        // ENTRADA y SALIDA se aplican con un $inc atómico en Mongo: el stock se lee y
        // se escribe en la misma operación, sin la carrera leer→restar→guardar de antes.
        switch (tipo) {
            case "ENTRADA":
                producto = productoService.incrementarStock(productoId, cantidad);
                stockNuevo = producto.getStockActual();
                stockAnterior = stockNuevo - cantidad;
                break;

            case "SALIDA":
                producto = productoService.descontarStock(productoId, cantidad);
                stockNuevo = producto.getStockActual();
                stockAnterior = stockNuevo + cantidad;
                break;

            case "AJUSTE":
                // Conteo físico: fija el valor absoluto.
                producto = productoService.obtenerPorId(productoId);
                stockAnterior = producto.getStockActual() != null ? producto.getStockActual() : 0;
                stockNuevo = cantidad;
                productoService.actualizarStock(productoId, stockNuevo);
                break;

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de movimiento inválido: ENTRADA, SALIDA o AJUSTE");
        }

        // Crear movimiento
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProductoId(productoId);
        movimiento.setTiendaId(producto.getTiendaId());
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
            LocalDateTime fechaFin, String tiendaId) {
        List<MovimientoInventario> movimientos;

        // Caso 1: todos sin filtros (o los de una tienda)
        movimientos = tiendaId != null
                ? movimientoRepository.findByTiendaIdOrderByFechaDesc(tiendaId)
                : movimientoRepository.findAllByOrderByFechaDesc();

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
        movimiento.setTiendaId(producto.getTiendaId());
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
