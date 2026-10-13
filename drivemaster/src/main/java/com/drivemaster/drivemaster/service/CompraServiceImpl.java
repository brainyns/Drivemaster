package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.DetalleCompra;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.util.PlantillaCorreo;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.CompraRepository;
import com.drivemaster.drivemaster.util.PdfCompraBuilder;

@Service
public class CompraServiceImpl implements CompraService {

    private static final Logger log = LoggerFactory.getLogger(CompraServiceImpl.class);

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
    public Compra registrarCompra(Compra compra, Tienda tienda) {
        if (tienda == null || tienda.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta la tienda dueña");
        }
        if (compra.getProductos() == null || compra.getProductos().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La compra necesita al menos un producto");
        }
        // El proveedor tiene que ser de esta tienda (404 si es de otra: no se revela que existe)
        proveedorService.obtenerDeTienda(compra.getProveedorId(), tienda.getId());

        compra.setId(null); // siempre es nueva
        compra.setTiendaId(tienda.getId());
        compra.setUsuarioId(tienda.getUsuarioId()); // el usuario sale de la sesión, no del cuerpo de la petición
        compra.setFecha(LocalDateTime.now());

        double total = 0;
        for (DetalleCompra detalle : compra.getProductos()) {
            if (detalle.getCantidad() == null || detalle.getCantidad() <= 0 || detalle.getCosto() == null || detalle.getCosto() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada producto necesita una cantidad mayor que cero y un costo válido");
            }
            Producto producto = productoService.obtenerPorId(detalle.getProductoId());
            if (!tienda.getId().equals(producto.getTiendaId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este producto no pertenece a tu tienda");
            }
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
                String filaEstilo = "padding:6px 0;font-size:14px;border-bottom:1px solid #e5e7eb;";
                String html = PlantillaCorreo.documento("Orden de compra",
                        tienda.getNombre() != null ? tienda.getNombre() : "DriveMaster",
                        PlantillaCorreo.parrafo("Estimado proveedor <strong>" + PlantillaCorreo.e(prov.getNombre()) + "</strong>,")
                        + PlantillaCorreo.parrafo("Adjunto encontrará la orden de compra <strong>" + poId + "</strong> registrada en nuestro sistema.")
                        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 16px;\">"
                        + "<tr><td style=\"" + filaEstilo + "color:#6b7280;\">Fecha</td>"
                        + "<td align=\"right\" style=\"" + filaEstilo + "color:#111827;\">"
                        + compraGuardada.getFecha().toString().replace("T", " ").substring(0, 16) + "</td></tr>"
                        + "<tr><td style=\"padding:10px 0 0;border-top:2px solid " + BrandConfig.colorPrimario() + ";font-size:15px;font-weight:700;color:#111827;\">Total</td>"
                        + "<td align=\"right\" style=\"padding:10px 0 0;border-top:2px solid " + BrandConfig.colorPrimario() + ";font-size:15px;font-weight:700;color:" + BrandConfig.colorPrimarioTexto() + ";\">$"
                        + String.format("%,.0f", compraGuardada.getTotal()) + "</td></tr>"
                        + "</table>",
                        "DriveMaster · Repuestos Automotrices · 2026");
                emailService.enviarEmailConAdjunto(
                        prov.getCorreo(),
                        "Orden de Compra " + poId + " - DriveMaster",
                        html,
                        pdf,
                        "Factura-Compra-" + poId + ".pdf"
                );
            }
        } catch (Exception e) {
            log.error("Error enviando correo de compra para compra {}: {}", compraGuardada.getId(), e.getMessage(), e);
        }

        return compraGuardada;
    }

    @Override
    public Compra obtenerDeTienda(String id, String tiendaId) {
        return compraRepository.findById(id)
                .filter(c -> tiendaId != null && tiendaId.equals(c.getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    @Override
    public List<Compra> listarDeTienda(String tiendaId) {
        if (tiendaId == null || tiendaId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta la tienda dueña");
        }
        return compraRepository.findByTiendaIdOrderByFechaDesc(tiendaId);
    }

    @Override
    public byte[] generarPdfDeTienda(String id, String tiendaId) {
        obtenerDeTienda(id, tiendaId); // 404 si no es suya
        return generarPdf(id);
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