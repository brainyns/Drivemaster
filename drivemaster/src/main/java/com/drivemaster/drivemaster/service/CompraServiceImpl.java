package com.drivemaster.drivemaster.service;


import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.DetalleCompra;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.repository.CompraRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompraServiceImpl implements CompraService {

    private final CompraRepository compraRepository;
    private final ProductoService productoService;
    private final MovimientoInventarioService movimientoService;

    public CompraServiceImpl(CompraRepository compraRepository, ProductoService productoService, MovimientoInventarioService movimientoService) {
        this.compraRepository = compraRepository;
        this.productoService = productoService;
        this.movimientoService = movimientoService;
    }

    @Override
    public Compra registrarCompra(Compra compra) {

        compra.setFecha(LocalDateTime.now());

        double total = 0;

        // Calcular subtotales y total
        for (DetalleCompra detalle : compra.getProductos()) {

            Producto producto = productoService.obtenerPorId(
                    detalle.getProductoId()
            );

            double subtotal = detalle.getCosto() * detalle.getCantidad();
            detalle.setNombre(producto.getNombre());
            detalle.setSubtotal(subtotal);

            total += subtotal;
        }

        compra.setTotal(total);

        // Guardar la compra 
        Compra compraGuardada = compraRepository.save(compra);

        // Registrar movimientos de inventario (ENTRADA)
        for (DetalleCompra detalle : compraGuardada.getProductos()) {

            movimientoService.registrarMovimiento(
                    detalle.getProductoId(),
                    "ENTRADA",
                    detalle.getCantidad(),
                    "Compra a proveedor",
                    compraGuardada.getId(),
                    compraGuardada.getUsuarioId()
            );
        }

        return compraGuardada;
    }

    @Override
    public Compra obtenerPorId(String id) {
        return compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));
    }

    @Override
    public List<Compra> listarTodas() {
        return compraRepository.findAll();
    }
}

