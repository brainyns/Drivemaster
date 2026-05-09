package com.drivemaster.drivemaster.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Cliente;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Venta;
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
    private final EmailsService                emailService;
    private final ClienteService               clienteService;

    public VentaServiceImpl(
            VentaRepository ventaRepository,
            ProductoService productoService,
            MovimientoInventarioService movimientoService,
            MetodoPagoRepository metodoPagoRepo,
            ParametroRepository parametroRepo,
            EmailsService emailService,
            ClienteService clienteService) {
        this.ventaRepository  = ventaRepository;
        this.productoService  = productoService;
        this.movimientoService = movimientoService;
        this.metodoPagoRepo   = metodoPagoRepo;
        this.parametroRepo    = parametroRepo;
        this.emailService     = emailService;
        this.clienteService   = clienteService;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

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

    // ── Registrar ─────────────────────────────────────────────────────────────

    @Override
    public Venta registrarVenta(Venta venta) {

        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        venta.setFecha(ahora);

        // Estado desde MySQL
        String estadoPagada = "PAGADA";
        var estadoParam = parametroRepo.findByClave("ESTADO_VENTA_DEFAULT");
        if (estadoParam.isPresent()) estadoPagada = estadoParam.get().getValor();
        venta.setEstado(estadoPagada);

        // IVA desde MySQL
        double iva = resolverIva();

        // Validar métodos de pago
        for (Pago pago : venta.getPagos()) {
            metodoPagoRepo.findByCodigo(pago.getMetodo())
                    .filter(mp -> mp.getActivo())
                    .orElseThrow(() -> new RuntimeException(
                            "Método de pago inválido o inactivo: " + pago.getMetodo()));
        }

        // Calcular subtotales
        double totalVenta = 0;
        for (DetalleVenta detalle : venta.getProductos()) {
            Producto producto = productoService.obtenerPorId(detalle.getProductoId());
            double subtotal  = producto.getPrecioVenta() * detalle.getCantidad();
            detalle.setNombre(producto.getNombre());
            detalle.setPrecioUnitario(producto.getPrecioVenta());
            detalle.setSubtotal(subtotal);
            totalVenta += subtotal;
        }

        // Aplicar IVA
        double totalConIva = totalVenta * (1 + iva);
        venta.setTotal(totalConIva);

        // Validar pagos
        double totalPagos = venta.getPagos().stream().mapToDouble(Pago::getMonto).sum();
        if (Double.compare(
                Math.round(totalPagos * 100.0) / 100.0,
                Math.round(totalConIva * 100.0) / 100.0) != 0) {
            throw new RuntimeException("El total de pagos no coincide con la venta");
        }

        // Completar referencias de pago
        String refBase = ahora.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        for (Pago pago : venta.getPagos()) {
            pago.setFecha(ahora);
            pago.setReferencia("RFF_" + refBase);
        }

        // Guardar
        Venta ventaGuardada = ventaRepository.save(venta);

        // Movimientos de inventario
        for (DetalleVenta detalle : ventaGuardada.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(), "SALIDA", detalle.getCantidad(),
                    "Venta", ventaGuardada.getId(), ventaGuardada.getUsuarioId());
        }

        // Correo + PDF adjunto
        Cliente cliente      = clienteService.obtenerPorId(ventaGuardada.getClienteId());
        String  idCorto      = idCorto(ventaGuardada.getId());
        String  htmlFactura  = EmailVentaBuilder.construir(cliente, ventaGuardada, iva);
        byte[]  pdfFactura   = PdfVentaBuilder.construir(cliente, ventaGuardada, iva);

        emailService.enviarEmailConAdjunto(
                cliente.getCorreo(),
                "Factura de venta #" + idCorto,
                htmlFactura,
                pdfFactura,
                "Factura-DriveMaster-" + idCorto + ".pdf"
        );

        return ventaGuardada;
    }

    // ── PDF individual ────────────────────────────────────────────────────────

    @Override
    public byte[] generarPdf(String id) {
        Venta   venta   = obtenerPorId(id);
        Cliente cliente = clienteService.obtenerPorId(venta.getClienteId());
        double  iva     = resolverIva();
        return PdfVentaBuilder.construir(cliente, venta, iva);
    }

    // ── Exportar Excel ────────────────────────────────────────────────────────

    @Override
    public byte[] exportarExcel() {
        List<Venta> ventas = listarTodas();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Ventas");

            // Estilo encabezado
            CellStyle hStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hFont = wb.createFont();
            hFont.setBold(true);
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Fila encabezado
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
                        ? "#VK-" + idCorto(v.getId()) : "—");
                row.createCell(1).setCellValue(v.getFecha() != null
                        ? v.getFecha().format(dtf) : "—");
                row.createCell(2).setCellValue(v.getClienteId() != null
                        ? v.getClienteId() : "—");
                row.createCell(3).setCellValue(v.getPagos() != null && !v.getPagos().isEmpty()
                        ? v.getPagos().get(0).getMetodo() : "—");
                row.createCell(4).setCellValue(v.getEstado() != null
                        ? v.getEstado() : "—");
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

    // ── Obtener / Listar / Anular ─────────────────────────────────────────────

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

        for (DetalleVenta detalle : venta.getProductos()) {
            movimientoService.registrarMovimiento(
                    detalle.getProductoId(), "ENTRADA", detalle.getCantidad(),
                    "Anulación de venta", ventaId, venta.getUsuarioId());
        }

        venta.setEstado("ANULADA");
        ventaRepository.save(venta);
    }
}