package com.drivemaster.drivemaster.service;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.VentaRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VentaServiceImpl implements VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final MovimientoInventarioService movimientoService;

    public VentaServiceImpl(VentaRepository ventaRepository, ProductoService productoService,
            MovimientoInventarioService movimientoService) {
        this.ventaRepository = ventaRepository;
        this.productoService = productoService;
        this.movimientoService = movimientoService;
    }

   @Override
public Venta registrarVenta(Venta venta) {

    // Instante único para toda la venta
    LocalDateTime ahora = LocalDateTime.now().withNano(0);

    // Fecha de la venta
    venta.setFecha(ahora);
    venta.setEstado("PAGADA");

    // Calcular total de la venta
    double totalVenta = 0;

    for (DetalleVenta detalle : venta.getProductos()) {

        Producto producto = productoService
                .obtenerPorId(detalle.getProductoId());

        double subtotal = producto.getPrecioVenta() * detalle.getCantidad();

        detalle.setNombre(producto.getNombre());
        detalle.setPrecioUnitario(producto.getPrecioVenta());
        detalle.setSubtotal(subtotal);

        totalVenta += subtotal;
    }

    venta.setTotal(totalVenta);

    // Validar pagos
    double totalPagos = venta.getPagos().stream()
            .mapToDouble(Pago::getMonto)
            .sum();

    if (Double.compare(totalPagos, totalVenta) != 0) {
        throw new RuntimeException(
                "El total de pagos no coincide con la venta"
        );
    }

    // Completar pagos
    for (Pago pago : venta.getPagos()) {
        pago.setFecha(ahora);
        pago.setReferencia("RFF_" +
                ahora.format(
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                )
        );
    }

    // Guardar venta 
    Venta ventaGuardada = ventaRepository.save(venta);

    // Movimientos de inventario
    for (DetalleVenta detalle : ventaGuardada.getProductos()) {
        movimientoService.registrarMovimiento(
                detalle.getProductoId(),
                "SALIDA",
                detalle.getCantidad(),
                "Venta",
                ventaGuardada.getId(),
                ventaGuardada.getUsuarioId()
        );
    }

    return ventaGuardada;
}


    @Override
    public Venta obtenerPorId(String id) {
        return ventaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
    }

    @Override
    public List<Venta> listarTodas() {
        return ventaRepository.findAll();
    }

    @Override
    public void anularVenta(String ventaId) {

        Venta venta = obtenerPorId(ventaId);

        if ("ANULADA".equals(venta.getEstado())) {
            throw new RuntimeException("La venta ya está anulada");
        }

        // Revertir inventario
        for (DetalleVenta detalle : venta.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(),
                    "ENTRADA",
                    detalle.getCantidad(),
                    "Anulación de venta",
                    ventaId,
                    venta.getUsuarioId());
        }

        venta.setEstado("ANULADA");
        ventaRepository.save(venta);
    }
}
