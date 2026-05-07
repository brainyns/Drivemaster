package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Cliente;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.VentaRepository; // ← NUEVO
import com.drivemaster.drivemaster.repository.mysql.MetodoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;
import com.drivemaster.drivemaster.util.EmailVentaBuilder;


@Service
public class VentaServiceImpl implements VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final MovimientoInventarioService movimientoService;
    private final MetodoPagoRepository metodoPagoRepo;
    private final ParametroRepository parametroRepo;
    private final EmailsService emailService;
    private final ClienteService clienteService;

    public VentaServiceImpl(
            VentaRepository ventaRepository,
            ProductoService productoService,
            MovimientoInventarioService movimientoService,
            MetodoPagoRepository metodoPagoRepo,
            ParametroRepository parametroRepo,
            EmailsService emailService,
            ClienteService clienteService) {
        this.ventaRepository = ventaRepository;
        this.productoService = productoService;
        this.movimientoService = movimientoService;
        this.metodoPagoRepo = metodoPagoRepo;
        this.parametroRepo = parametroRepo;
        this.emailService = emailService;
        this.clienteService = clienteService;
    }

    @Override
    public Venta registrarVenta(Venta venta) {

        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        venta.setFecha(ahora);

        // ── Estado desde MySQL ────────────────────────
        String estadoPagada = "PAGADA";
        var estadoParam = parametroRepo.findByClave("ESTADO_VENTA_DEFAULT");
        if (estadoParam.isPresent()) {
            estadoPagada = estadoParam.get().getValor();
        }
        venta.setEstado(estadoPagada);

        // ── IVA desde MySQL ───────────────────────────
        double iva = 0.16;
        var ivaParam = parametroRepo.findByClave("IVA");
        if (ivaParam.isPresent()) {
            iva = Double.parseDouble(ivaParam.get().getValor()) / 100.0;
        }

        // ── Validar métodos de pago contra MySQL ──────
        for (Pago pago : venta.getPagos()) {
            metodoPagoRepo.findByCodigo(pago.getMetodo())
                    .filter(mp -> mp.getActivo())
                    .orElseThrow(() -> new RuntimeException(
                            "Método de pago inválido o inactivo: " + pago.getMetodo()));
        }

        // ── Calcular total ────────────────────────────
        double totalVenta = 0;
        for (DetalleVenta detalle : venta.getProductos()) {
            Producto producto = productoService.obtenerPorId(detalle.getProductoId());
            double subtotal = producto.getPrecioVenta() * detalle.getCantidad();
            detalle.setNombre(producto.getNombre());
            detalle.setPrecioUnitario(producto.getPrecioVenta());
            detalle.setSubtotal(subtotal);
            totalVenta += subtotal;
        }

        // ── Aplicar IVA ───────────────────────────────
        double totalConIva = totalVenta * (1 + iva);
        venta.setTotal(totalConIva);

        // ── Validar pagos ─────────────────────────────
        double totalPagos = venta.getPagos().stream()
                .mapToDouble(Pago::getMonto)
                .sum();

        if (Double.compare(Math.round(totalPagos * 100.0) / 100.0,
                Math.round(totalConIva * 100.0) / 100.0) != 0) {
            throw new RuntimeException("El total de pagos no coincide con la venta");
        }

        // ── Completar pagos ───────────────────────────
        String refBase = ahora.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        for (Pago pago : venta.getPagos()) {
            pago.setFecha(ahora);
            pago.setReferencia("RFF_" + refBase);
        }

        // ── Guardar en MongoDB ────────────────────────
        Venta ventaGuardada = ventaRepository.save(venta);

        // ── Movimientos de inventario ─────────────────
        for (DetalleVenta detalle : ventaGuardada.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(),
                    "SALIDA",
                    detalle.getCantidad(),
                    "Venta",
                    ventaGuardada.getId(),
                    ventaGuardada.getUsuarioId());
        }

        // ── Enviar correo electrónico ─────────────────
        Cliente cliente = clienteService.obtenerPorId(ventaGuardada.getClienteId());

        String htmlFactura = EmailVentaBuilder.construir(cliente, ventaGuardada, iva); // ← NUEVO

        emailService.enviarEmail(
                cliente.getCorreo(),
                "Factura de venta #" + ventaGuardada.getId()
                        .substring(ventaGuardada.getId().length() - 8)
                        .toUpperCase(),
                htmlFactura // ← NUEVO: antes era el texto plano
        );

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

        String estadoAnulada = "ANULADA";

        if ("ANULADA".equals(venta.getEstado())) {
            throw new RuntimeException("La venta ya está anulada");
        }

        for (DetalleVenta detalle : venta.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(),
                    "ENTRADA",
                    detalle.getCantidad(),
                    "Anulación de venta",
                    ventaId,
                    venta.getUsuarioId());
        }

        venta.setEstado(estadoAnulada);
        ventaRepository.save(venta);
    }
}