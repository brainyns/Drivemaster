package com.drivemaster.drivemaster.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.dto.CartItemRequest;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.VentaRepository;
import com.drivemaster.drivemaster.repository.mysql.MetodoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;
import com.drivemaster.drivemaster.util.EmailVentaBuilder;
import com.drivemaster.drivemaster.util.PdfVentaBuilder;

@Service
public class VentaServiceImpl implements VentaService {

    private final VentaRepository              ventaRepository;
    private final ProductoService              productoService;
    private final MovimientoInventarioService  movimientoService;
    private final MetodoPagoRepository         metodoPagoRepo;
    private final ParametroRepository          parametroRepo;
    private final EmailsService emailService;
    private final UsuarioRepository usuarioRepository;

    public VentaServiceImpl(
            VentaRepository ventaRepository,
            ProductoService productoService,
            MovimientoInventarioService movimientoService,
            MetodoPagoRepository metodoPagoRepo,
            ParametroRepository parametroRepo,
            EmailsService emailService,
            UsuarioRepository usuarioRepository) {
        this.ventaRepository  = ventaRepository;
        this.productoService  = productoService;
        this.movimientoService = movimientoService;
        this.metodoPagoRepo   = metodoPagoRepo;
        this.parametroRepo    = parametroRepo;
        this.emailService     = emailService;
        this.usuarioRepository = usuarioRepository;
    }

    private double resolverIva() {
        double iva = 0.16;
        var ivaParam = parametroRepo.findByClave("IVA");
        if (ivaParam.isPresent()) {
            iva = Double.parseDouble(ivaParam.get().getValor()) / 100.0;
        }
        return iva;
    }

    private String idCorto(String id) {
        return id.substring(Math.max(0, id.length() - 8)).toUpperCase();
    }

    @Override
    public Venta registrarVenta(Venta venta) {

        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        venta.setFecha(ahora);

        if (venta.getEstado() == null) {
            String estadoPagada = "PAGADA";
            var estadoParam = parametroRepo.findByClave("ESTADO_VENTA_DEFAULT");
            if (estadoParam.isPresent()) estadoPagada = estadoParam.get().getValor();
            venta.setEstado(estadoPagada);
        }

        double iva = resolverIva();

        for (Pago pago : venta.getPagos()) {
            metodoPagoRepo.findByCodigo(pago.getMetodo())
                    .filter(mp -> mp.getActivo())
                    .orElseThrow(() -> new RuntimeException(
                            "Método de pago inválido o inactivo: " + pago.getMetodo()));
        }

        double totalVenta = 0;
        for (DetalleVenta detalle : venta.getProductos()) {
            Producto producto = productoService.obtenerPorId(detalle.getProductoId());
            double subtotal  = producto.getPrecioVenta() * detalle.getCantidad();
            detalle.setNombre(producto.getNombre());
            detalle.setPrecioUnitario(producto.getPrecioVenta());
            detalle.setSubtotal(subtotal);
            totalVenta += subtotal;
        }

        double totalConIva = totalVenta * (1 + iva);
        venta.setTotal(totalConIva);

        double totalPagos = venta.getPagos().stream().mapToDouble(Pago::getMonto).sum();
        if (Double.compare(
                Math.round(totalPagos * 100.0) / 100.0,
                Math.round(totalConIva * 100.0) / 100.0) != 0) {
            throw new RuntimeException("El total de pagos no coincide con la venta");
        }

        String refBase = ahora.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        for (Pago pago : venta.getPagos()) {
            pago.setFecha(ahora);
            pago.setReferencia("RFF_" + refBase);
        }

        Venta ventaGuardada = ventaRepository.save(venta);

        for (DetalleVenta detalle : ventaGuardada.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(), "SALIDA", detalle.getCantidad(),
                    "Venta", ventaGuardada.getId(), ventaGuardada.getUsuarioId());
        }

        Usuario usuario = usuarioRepository.findById(ventaGuardada.getClienteId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String idCorto = idCorto(ventaGuardada.getId());
        String htmlFactura = EmailVentaBuilder.construir(usuario, ventaGuardada, iva);
        byte[] pdfFactura = PdfVentaBuilder.construir(usuario, ventaGuardada, iva);

        emailService.enviarEmailConAdjunto(
                usuario.getCorreo(),
                "Factura de venta #" + idCorto,
                htmlFactura,
                pdfFactura,
                "Factura-DriveMaster-" + idCorto + ".pdf"
        );

        return ventaGuardada;
    }

    @Override
    public byte[] generarPdf(String id) {
        Venta   venta   = obtenerPorId(id);
        Usuario usuario = usuarioRepository.findById(venta.getClienteId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        double  iva     = resolverIva();
        return PdfVentaBuilder.construir(usuario, venta, iva);
    }

    @Override
    public byte[] exportarExcel() {
        List<Venta> ventas = listarTodas();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Ventas");

            CellStyle hStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hFont = wb.createFont();
            hFont.setBold(true);
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] cols = { "ID", "Fecha", "Cliente ID", "Método de Pago", "Estado", "Total" };
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            int rowNum = 1;
            for (Venta v : ventas) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(v.getId() != null
                        ? "#VK-" + idCorto(v.getId()) : "\u2014");
                row.createCell(1).setCellValue(v.getFecha() != null
                        ? v.getFecha().format(dtf) : "\u2014");
                row.createCell(2).setCellValue(v.getClienteId() != null
                        ? v.getClienteId() : "\u2014");
                row.createCell(3).setCellValue(v.getPagos() != null && !v.getPagos().isEmpty()
                        ? v.getPagos().get(0).getMetodo() : "\u2014");
                row.createCell(4).setCellValue(v.getEstado() != null
                        ? v.getEstado() : "\u2014");
                row.createCell(5).setCellValue(v.getTotal() != null
                        ? v.getTotal() : 0.0);
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Error al generar el Excel de ventas", e);
        }
    }

    private Usuario resolverUsuario(String email) {
        return usuarioRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + email));
    }

    @Override
    public Venta compraInmediata(String email, List<CartItemRequest> items, String metodoPago) {
        Usuario usuario = resolverUsuario(email);

        if (usuario.getTelefono() == null || usuario.getDireccion() == null || usuario.getCiudad() == null) {
            throw new RuntimeException("Complete sus datos de env\u00edo primero.");
        }

        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Debe incluir al menos un producto.");
        }

        List<DetalleVenta> detalles = new java.util.ArrayList<>();
        for (CartItemRequest req : items) {
            Producto prod = productoService.obtenerPorId(req.getProductoId());
            String tipo = prod.getTipo() != null ? prod.getTipo() : "STOCK";

            if (!"STOCK".equals(tipo)) {
                throw new RuntimeException("El producto \"" + prod.getNombre()
                        + "\" es por encargo y no puede comprarse de forma inmediata.");
            }
            if (req.getCantidad() > prod.getStockActual()) {
                throw new StockInsuficienteException(prod.getNombre(), prod.getStockActual());
            }

            DetalleVenta d = new DetalleVenta();
            d.setProductoId(prod.getId());
            d.setNombre(prod.getNombre());
            d.setImagenUrl(prod.getImagenUrl());
            d.setPrecioUnitario(prod.getPrecioVenta());
            d.setCantidad(req.getCantidad());
            d.setSubtotal(prod.getPrecioVenta() * req.getCantidad());
            d.setTipo(tipo);
            d.setStockActual(prod.getStockActual());
            detalles.add(d);
        }

        double subtotal = detalles.stream().mapToDouble(DetalleVenta::getSubtotal).sum();
        double iva = subtotal * resolverIva();
        double total = subtotal + iva;

        Venta venta = new Venta();
        venta.setClienteId(usuario.getId());
        venta.setUsuarioId(usuario.getId());
        venta.setEstado("APROBADO");
        venta.setTipoVenta("WEB");
        venta.setProductos(detalles);
        venta.setTotal(total);

        Pago pago = new Pago(metodoPago, total, LocalDateTime.now(), null);
        venta.setPagos(List.of(pago));

        return this.registrarVenta(venta);
    }

    @Override
    public Venta obtenerPorId(String id) {
        return ventaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
    }

    @Override
    public List<Venta> listarTodas() {
        return ventaRepository.findAll(Sort.by(Sort.Direction.DESC, "fecha"));
    }

    @Override
    public List<Venta> listarPorUsuarioId(String usuarioId) {
        return ventaRepository.findByUsuarioId(usuarioId);
    }

    @Override
    public void anularVenta(String ventaId) {
        Venta venta = obtenerPorId(ventaId);

        if ("ANULADA".equals(venta.getEstado())) {
            throw new RuntimeException("La venta ya está anulada");
        }

        for (DetalleVenta detalle : venta.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(), "ENTRADA", detalle.getCantidad(),
                    "Anulación de venta", ventaId, venta.getUsuarioId());
        }

        venta.setEstado("ANULADA");
        ventaRepository.save(venta);
    }
}
