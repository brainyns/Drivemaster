package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.DetalleCompra;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.repository.CompraRepository;
import com.drivemaster.drivemaster.util.PdfCompraBuilder;

@Service
public class CompraServiceImpl implements CompraService {

    private final CompraRepository            compraRepository;
    private final ProductoService             productoService;
    private final MovimientoInventarioService movimientoService;
    private final ProveedorService            proveedorService;
    private final EmailsService               emailService;

    public CompraServiceImpl(
            CompraRepository compraRepository,
            ProductoService productoService,
            MovimientoInventarioService movimientoService,
            ProveedorService proveedorService,
            EmailsService emailService) {
        this.compraRepository  = compraRepository;
        this.productoService   = productoService;
        this.movimientoService = movimientoService;
        this.proveedorService  = proveedorService;
        this.emailService      = emailService;
    }

    @Override
    public Compra registrarCompra(Compra compra) {
        compra.setFecha(LocalDateTime.now());

        double total = 0;
        for (DetalleCompra detalle : compra.getProductos()) {
            Producto producto = productoService.obtenerPorId(detalle.getProductoId());
            double subtotal   = detalle.getCosto() * detalle.getCantidad();
            detalle.setNombre(producto.getNombre());
            detalle.setSubtotal(subtotal);
            total += subtotal;
        }
        compra.setTotal(total);

        Compra compraGuardada = compraRepository.save(compra);

        for (DetalleCompra detalle : compraGuardada.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(), "ENTRADA", detalle.getCantidad(),
                    "Compra a proveedor", compraGuardada.getId(), compraGuardada.getUsuarioId());
        }

        // Enviar correo con PDF adjunto al proveedor
        try {
            Proveedor prov = proveedorService.obtenerPorId(compraGuardada.getProveedorId());
            if (prov.getCorreo() != null && !prov.getCorreo().isBlank()) {
                byte[] pdf = generarPdf(compraGuardada.getId());
                String poId = "PO-" + compraGuardada.getId()
                        .substring(Math.max(0, compraGuardada.getId().length() - 5))
                        .toUpperCase();
                String html = "<div style='font-family:sans-serif;max-width:600px;margin:0 auto'>"
                        + "<h2 style='color:#E8450A'>DriveMaster</h2>"
                        + "<p>Estimado proveedor <strong>" + prov.getNombre() + "</strong>,</p>"
                        + "<p>Adjunto encontrará la orden de compra <strong>" + poId + "</strong> registrada en nuestro sistema.</p>"
                        + "<table style='width:100%;border-collapse:collapse;margin:1rem 0'>"
                        + "<tr><td style='padding:.4rem;color:#888'>Total</td>"
                        + "<td style='padding:.4rem;font-weight:700'>$" + String.format("%,.0f", compraGuardada.getTotal()) + "</td></tr>"
                        + "<tr><td style='padding:.4rem;color:#888'>Fecha</td>"
                        + "<td style='padding:.4rem'>" + compraGuardada.getFecha().toString().replace("T", " ").substring(0, 16) + "</td></tr>"
                        + "</table>"
                        + "<p style='color:#888;font-size:12px'>DriveMaster · Repuestos Automotrices · 2026</p>"
                        + "</div>";
                emailService.enviarEmailConAdjunto(
                        prov.getCorreo(),
                        "Orden de Compra " + poId + " - DriveMaster",
                        html,
                        pdf,
                        "Factura-Compra-" + poId + ".pdf"
                );
            }
        } catch (Exception e) {
            System.err.println("Error enviando correo de compra: " + e.getMessage());
        }

        return compraGuardada;
    }

    @Override
    public byte[] generarPdf(String id) {
        Compra compra = obtenerPorId(id);
        String provNombre = "Proveedor";
        String provNit    = "";
        String provCorreo = "";
        try {
            Proveedor prov = proveedorService.obtenerPorId(compra.getProveedorId());
            provNombre = prov.getNombre();
            provNit    = prov.getNit()    != null ? prov.getNit()    : "";
            provCorreo = prov.getCorreo() != null ? prov.getCorreo() : "";
        } catch (Exception ignored) {}
        return PdfCompraBuilder.construir(compra, provNombre, provNit, provCorreo);
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