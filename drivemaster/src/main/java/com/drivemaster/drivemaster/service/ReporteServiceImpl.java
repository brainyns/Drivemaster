package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.dto.reporte.ClienteInactivoDTO;
import com.drivemaster.drivemaster.dto.reporte.ClienteReporteDTO;
import com.drivemaster.drivemaster.dto.reporte.DashboardDTO;
import com.drivemaster.drivemaster.dto.reporte.HistorialClienteDTO;
import com.drivemaster.drivemaster.dto.reporte.InventarioClientesDTO;
import com.drivemaster.drivemaster.dto.reporte.InventarioProductosDTO;
import com.drivemaster.drivemaster.dto.reporte.ProductoReporteDTO;
import com.drivemaster.drivemaster.dto.reporte.ReporteClientesDTO;
import com.drivemaster.drivemaster.dto.reporte.ReporteProductosDTO;
import com.drivemaster.drivemaster.dto.reporte.VentaResumenDTO;
import com.drivemaster.drivemaster.model.Cliente;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.ClienteRepository;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.VentaRepository;
 
@Service
public class ReporteServiceImpl implements ReporteService {
 
    private final VentaRepository    ventaRepository;
    private final ClienteRepository  clienteRepository;
    private final ProductoRepository productoRepository;
 
    public ReporteServiceImpl(VentaRepository ventaRepository,
                               ClienteRepository clienteRepository,
                               ProductoRepository productoRepository) {
        this.ventaRepository    = ventaRepository;
        this.clienteRepository  = clienteRepository;
        this.productoRepository = productoRepository;
    }
 
    // ── Utilidades de fecha ───────────────────────────────────────────────────
    private LocalDateTime inicioDia()   { return LocalDateTime.now().toLocalDate().atStartOfDay(); }
    private LocalDateTime inicioSemana(){ return inicioDia().minusDays(7); }
    private LocalDateTime inicioMes()   { return inicioDia().minusDays(30); }
 
    private LocalDateTime inicioPeriodo(String periodo) {
        return switch (periodo.toLowerCase()) {
            case "dia"    -> inicioDia();
            case "semana" -> inicioSemana();
            default       -> inicioMes();
        };
    }
 
    private List<Venta> ventasActivas() {
        return ventaRepository.findAll().stream()
                .filter(v -> !"ANULADA".equals(v.getEstado()) && !"CANCELADA".equals(v.getEstado()))
                .collect(Collectors.toList());
    }
 
    // ── DASHBOARD ─────────────────────────────────────────────────────────────
    @Override
    public DashboardDTO getDashboard() {
        DashboardDTO dto = new DashboardDTO();
        List<Venta> activas = ventasActivas();
 
        // Total ventas día
        dto.setTotalVentasDia(activas.stream()
                .filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioDia()))
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0)
                .sum());
 
        // Total ventas mes
        dto.setTotalVentasMes(activas.stream()
                .filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioMes()))
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0)
                .sum());
 
        // Total transacciones
        dto.setTotalTransacciones((long) activas.size());
 
        // Ticket promedio
        dto.setPromedioTicket(activas.isEmpty() ? 0 :
                activas.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).average().orElse(0));
 
        // Producto más vendido
        Map<String, Long> conteoProductos = new HashMap<>();
        for (Venta v : activas) {
            if (v.getProductos() != null) {
                for (DetalleVenta d : v.getProductos()) {
                    conteoProductos.merge(d.getNombre(), (long) d.getCantidad(), Long::sum);
                }
            }
        }
        dto.setProductoMasVendido(conteoProductos.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—"));
 
        // Cliente más importante (por total gastado)
        Map<String, Double> gastoCliente = new HashMap<>();
        for (Venta v : activas) {
            if (v.getClienteId() != null) {
                gastoCliente.merge(v.getClienteId(), v.getTotal() == null ? 0.0 : v.getTotal(), Double::sum);
            }
        }
        String topClienteId = gastoCliente.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        if (topClienteId != null) {
            clienteRepository.findById(topClienteId)
                    .ifPresent(c -> dto.setClienteMasImportante(c.getNombre()));
        } else {
            dto.setClienteMasImportante("—");
        }
 
        // Método de pago más usado
        Map<String, Long> conteoMetodos = new HashMap<>();
        for (Venta v : activas) {
            if (v.getPagos() != null) {
                for (Pago p : v.getPagos()) {
                    if (p.getMetodo() != null) {
                        conteoMetodos.merge(p.getMetodo(), 1L, Long::sum);
                    }
                }
            }
        }
        dto.setMetodoPagoMasUsado(conteoMetodos.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—"));
 
        // Variación semana
        double ventasSemanaActual = activas.stream()
                .filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioSemana()))
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).sum();
        double ventasSemanaAnterior = activas.stream()
                .filter(v -> v.getFecha() != null
                        && v.getFecha().isAfter(inicioSemana().minusDays(7))
                        && v.getFecha().isBefore(inicioSemana()))
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).sum();
        dto.setVariacionSemana(ventasSemanaAnterior == 0 ? 0 :
                ((ventasSemanaActual - ventasSemanaAnterior) / ventasSemanaAnterior) * 100);
 
        return dto;
    }
 
    // ── INVENTARIO CLIENTES ───────────────────────────────────────────────────
    @Override
    public InventarioClientesDTO getInventarioClientes() {
        InventarioClientesDTO dto = new InventarioClientesDTO();
        List<Cliente> clientes = clienteRepository.findAll();
        List<Venta>   activas  = ventasActivas();
 
        List<ClienteReporteDTO> lista = clientes.stream()
                .map(c -> buildClienteReporteDTO(c, activas))
                .sorted(Comparator.comparingDouble(ClienteReporteDTO::getTotalGastado).reversed())
                .collect(Collectors.toList());
 
        dto.setTotalClientes((long) clientes.size());
        dto.setClientesFrecuentes(lista.stream().filter(c -> "FRECUENTE".equals(c.getClasificacion())).count());
        dto.setClientesOcasionales(lista.stream().filter(c -> "OCASIONAL".equals(c.getClasificacion())).count());
        dto.setClientesNuevos(lista.stream().filter(c -> "NUEVO".equals(c.getClasificacion())).count());
        dto.setClientes(lista);
        return dto;
    }
 
    private ClienteReporteDTO buildClienteReporteDTO(Cliente c, List<Venta> activas) {
        ClienteReporteDTO dto = new ClienteReporteDTO();
        dto.setId(c.getId());
        dto.setNombre(c.getNombre());
        dto.setIdentificacion(c.getIdentificacion());
        dto.setCorreo(c.getCorreo());
        dto.setTelefono(c.getTelefono());
 
        List<Venta> compras = activas.stream()
                .filter(v -> c.getId().equals(v.getClienteId()))
                .sorted(Comparator.comparing(Venta::getFecha, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
 
        dto.setTotalCompras((long) compras.size());
        dto.setTotalGastado(compras.stream().mapToDouble(v -> v.getTotal() == null ? 0.0 : v.getTotal()).sum());
 
        if (!compras.isEmpty() && compras.get(0).getFecha() != null) {
            dto.setUltimaCompra(compras.get(0).getFecha()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        } else {
            dto.setUltimaCompra("—");
        }
 
        // Método de pago más usado
        Map<String, Long> metodos = new HashMap<>();
        for (Venta v : compras) {
            if (v.getPagos() != null) {
                for (Pago p : v.getPagos()) {
                    if (p.getMetodo() != null) metodos.merge(p.getMetodo(), 1L, Long::sum);
                }
            }
        }
        dto.setMetodoPagoMasUsado(metodos.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—"));
 
        // Clasificación
        long n = dto.getTotalCompras();
        dto.setClasificacion(n == 0 ? "NUEVO" : n >= 10 ? "FRECUENTE" : "OCASIONAL");
 
        return dto;
    }
 
    // ── REPORTES CLIENTES ─────────────────────────────────────────────────────
    @Override
    public ReporteClientesDTO getReportesClientes(String periodo) {
        ReporteClientesDTO dto = new ReporteClientesDTO();
        dto.setPeriodo(periodo);
        List<Venta> activas = ventasActivas();
        List<Cliente> clientes = clienteRepository.findAll();
 
        LocalDateTime inicio = inicioPeriodo(periodo);
        List<Venta> enPeriodo = activas.stream()
                .filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicio))
                .collect(Collectors.toList());
 
        dto.setTotalRecaudado(enPeriodo.stream()
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).sum());
 
        // Cliente más compró hoy
        Map<String, Double> gastoDia = new HashMap<>();
        activas.stream().filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioDia()))
                .forEach(v -> gastoDia.merge(v.getClienteId(), v.getTotal() == null ? 0.0 : v.getTotal(), Double::sum));
        String topHoyId = gastoDia.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        dto.setClienteMasComproHoy(topHoyId != null ?
                clienteRepository.findById(topHoyId).map(Cliente::getNombre).orElse("—") : "—");
 
        // Cliente más compró semana
        Map<String, Double> gastoSemana = new HashMap<>();
        activas.stream().filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioSemana()))
                .forEach(v -> gastoSemana.merge(v.getClienteId(), v.getTotal() == null ? 0.0 : v.getTotal(), Double::sum));
        String topSemanaId = gastoSemana.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        dto.setClienteMasComproSemana(topSemanaId != null ?
                clienteRepository.findById(topSemanaId).map(Cliente::getNombre).orElse("—") : "—");
 
        // Ranking top 10
        dto.setRankingClientes(clientes.stream()
                .map(c -> buildClienteReporteDTO(c, enPeriodo))
                .sorted(Comparator.comparingDouble(ClienteReporteDTO::getTotalGastado).reversed())
                .limit(10)
                .collect(Collectors.toList()));
 
        // Clientes inactivos (sin comprar en 30 días)
        dto.setClientesInactivos(clientes.stream().map(c -> {
            List<Venta> compras = activas.stream()
                    .filter(v -> c.getId().equals(v.getClienteId()) && v.getFecha() != null)
                    .sorted(Comparator.comparing(Venta::getFecha).reversed())
                    .collect(Collectors.toList());
            if (compras.isEmpty()) return null;
            LocalDateTime ultima = compras.get(0).getFecha();
            long dias = ChronoUnit.DAYS.between(ultima, LocalDateTime.now());
            if (dias < 30) return null;
            ClienteInactivoDTO ci = new ClienteInactivoDTO();
            ci.setId(c.getId());
            ci.setNombre(c.getNombre());
            ci.setUltimaCompra(ultima.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            ci.setDiasSinComprar(dias);
            return ci;
        }).filter(Objects::nonNull)
          .sorted(Comparator.comparingLong(ClienteInactivoDTO::getDiasSinComprar).reversed())
          .collect(Collectors.toList()));
 
        // Ventas por día (últimos 7 días) para gráfica
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        Map<String, Double> ventasPorDia = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dia = inicioDia().minusDays(i);
            String key = dia.format(fmt);
            double sum = activas.stream()
                    .filter(v -> v.getFecha() != null
                            && v.getFecha().toLocalDate().equals(dia.toLocalDate()))
                    .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).sum();
            ventasPorDia.put(key, sum);
        }
        dto.setVentasPorDia(ventasPorDia);
 
        return dto;
    }
 
    // ── HISTORIAL CLIENTE ─────────────────────────────────────────────────────
    @Override
    public HistorialClienteDTO getHistorialCliente(String clienteId) {
        HistorialClienteDTO dto = new HistorialClienteDTO();
        dto.setClienteId(clienteId);
        clienteRepository.findById(clienteId).ifPresent(c -> dto.setNombre(c.getNombre()));
 
        List<VentaResumenDTO> compras = ventaRepository.findByClienteId(clienteId).stream()
                .filter(v -> !"ANULADA".equals(v.getEstado()))
                .sorted(Comparator.comparing(Venta::getFecha, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(v -> {
                    VentaResumenDTO r = new VentaResumenDTO();
                    r.setId(v.getId());
                    r.setFecha(v.getFecha() != null ?
                            v.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—");
                    r.setTotal(v.getTotal());
                    r.setEstado(v.getEstado());
                    r.setCantidadProductos(v.getProductos() != null ? v.getProductos().size() : 0);
                    if (v.getPagos() != null && !v.getPagos().isEmpty()) {
                        r.setMetodoPago(v.getPagos().get(0).getMetodo());
                    }
                    return r;
                }).collect(Collectors.toList());
 
        dto.setCompras(compras);
        return dto;
    }
 
    // ── INVENTARIO PRODUCTOS ──────────────────────────────────────────────────
    @Override
    public InventarioProductosDTO getInventarioProductos() {
        InventarioProductosDTO dto = new InventarioProductosDTO();
        List<Producto> productos = productoRepository.findAll();
        List<Venta>    activas   = ventasActivas();
 
        List<ProductoReporteDTO> lista = productos.stream()
                .map(p -> buildProductoReporteDTO(p, activas))
                .sorted(Comparator.comparingLong(ProductoReporteDTO::getUnidadesVendidas).reversed())
                .collect(Collectors.toList());
 
        dto.setTotalProductos((long) productos.size());
        dto.setProductosAgotados(lista.stream().filter(p -> "AGOTADO".equals(p.getEstadoStock())).count());
        dto.setProductosBajoStock(lista.stream().filter(p -> "BAJO".equals(p.getEstadoStock()) || "CRITICO".equals(p.getEstadoStock())).count());
        dto.setValorTotalInventario(lista.stream().mapToDouble(p ->
                (p.getPrecioVenta() == null ? 0.0 : p.getPrecioVenta()) *
                (p.getStockActual() == null ? 0 : p.getStockActual())).sum());
        dto.setProductos(lista);
        return dto;
    }
 
    private ProductoReporteDTO buildProductoReporteDTO(Producto p, List<Venta> activas) {
        ProductoReporteDTO dto = new ProductoReporteDTO();
        dto.setId(p.getId());
        dto.setCodigo(p.getCodigo());
        dto.setNombre(p.getNombre());
        dto.setCategoria(p.getCategoria());
        dto.setMarca(p.getMarca());
        dto.setStockActual(p.getStockActual());
        dto.setStockMinimo(p.getStockMinimo());
        dto.setPrecioVenta(p.getPrecioVenta());
 
        // Estado stock
        int stock = p.getStockActual() == null ? 0 : p.getStockActual();
        int min   = p.getStockMinimo()  == null ? 0 : p.getStockMinimo();
        if (stock == 0)          dto.setEstadoStock("AGOTADO");
        else if (stock <= min)   dto.setEstadoStock("CRITICO");
        else if (stock <= min*2) dto.setEstadoStock("BAJO");
        else                     dto.setEstadoStock("NORMAL");
 
        // Unidades vendidas y total generado
        long unidades = 0;
        double total  = 0;
        for (Venta v : activas) {
            if (v.getProductos() != null) {
                for (DetalleVenta d : v.getProductos()) {
                    if (p.getId().equals(d.getProductoId())) {
                        unidades += d.getCantidad() == null ? 0 : d.getCantidad();
                        total    += d.getSubtotal()  == null ? 0.0 : d.getSubtotal();
                    }
                }
            }
        }
        dto.setUnidadesVendidas(unidades);
        dto.setTotalGenerado(total);
        return dto;
    }
 
    // ── REPORTES PRODUCTOS ────────────────────────────────────────────────────
    @Override
    public ReporteProductosDTO getReportesProductos(String periodo) {
        ReporteProductosDTO dto = new ReporteProductosDTO();
        dto.setPeriodo(periodo);
 
        List<Producto> productos = productoRepository.findAll();
        LocalDateTime  inicio    = inicioPeriodo(periodo);
        List<Venta>    enPeriodo = ventasActivas().stream()
                .filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicio))
                .collect(Collectors.toList());
 
        List<ProductoReporteDTO> lista = productos.stream()
                .map(p -> buildProductoReporteDTO(p, enPeriodo))
                .collect(Collectors.toList());
 
        // Más vendidos
        dto.setMasVendidos(lista.stream()
                .filter(p -> p.getUnidadesVendidas() > 0)
                .sorted(Comparator.comparingLong(ProductoReporteDTO::getUnidadesVendidas).reversed())
                .limit(10).collect(Collectors.toList()));
 
        // Menos vendidos (con al menos 1 venta)
        dto.setMenosVendidos(lista.stream()
                .filter(p -> p.getUnidadesVendidas() > 0)
                .sorted(Comparator.comparingLong(ProductoReporteDTO::getUnidadesVendidas))
                .limit(10).collect(Collectors.toList()));
 
        // Sin movimiento
        dto.setSinMovimiento(lista.stream()
                .filter(p -> p.getUnidadesVendidas() == 0)
                .collect(Collectors.toList()));
 
        // Ventas por día (últimos 7 días)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        Map<String, Double> ventasPorDia = new LinkedHashMap<>();
        List<Venta> todasActivas = ventasActivas();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dia = LocalDateTime.now().toLocalDate().atStartOfDay().minusDays(i);
            String key = dia.format(fmt);
            double sum = todasActivas.stream()
                    .filter(v -> v.getFecha() != null && v.getFecha().toLocalDate().equals(dia.toLocalDate()))
                    .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).sum();
            ventasPorDia.put(key, sum);
        }
        dto.setVentasPorDia(ventasPorDia);
 
        // Unidades por categoría
        Map<String, Long> porCategoria = new LinkedHashMap<>();
        for (ProductoReporteDTO p : lista) {
            if (p.getCategoria() != null) {
                porCategoria.merge(p.getCategoria(), p.getUnidadesVendidas(), Long::sum);
            }
        }
        dto.setUnidadesPorCategoria(porCategoria);
 
        return dto;
    }
}