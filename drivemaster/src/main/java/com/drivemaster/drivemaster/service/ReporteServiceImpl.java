package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.drivemaster.drivemaster.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.dto.OrdenServicioDTO;
import com.drivemaster.drivemaster.dto.reporte.ClienteInactivoDTO;
import com.drivemaster.drivemaster.dto.reporte.ClienteReporteDTO;
import com.drivemaster.drivemaster.dto.reporte.DashboardDTO;
import com.drivemaster.drivemaster.dto.reporte.HistorialClienteDTO;
import com.drivemaster.drivemaster.dto.reporte.InventarioClientesDTO;
import com.drivemaster.drivemaster.dto.reporte.InventarioProductosDTO;
import com.drivemaster.drivemaster.dto.reporte.ProductoReporteDTO;
import com.drivemaster.drivemaster.dto.reporte.ReporteClientesDTO;
import com.drivemaster.drivemaster.dto.reporte.ReporteProductosDTO;
import com.drivemaster.drivemaster.dto.reporte.ReporteServiciosDTO;
import com.drivemaster.drivemaster.dto.reporte.VentaResumenDTO;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.VentaRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
 
@Service
public class ReporteServiceImpl implements ReporteService {

    /** Estados en los que una orden de taller ya está cobrada. */
    private static final Set<String> ESTADOS_COBRADOS =
            Set.of("PAGADA", "EN_PROCESO", "FINALIZADA");

    private final VentaRepository     ventaRepository;
    private final UsuarioRepository   usuarioRepository;
    private final ProductoRepository  productoRepository;
    private final OrdenServicioRepository ordenServicioRepository;

    public ReporteServiceImpl(VentaRepository ventaRepository,
                               UsuarioRepository usuarioRepository,
                               ProductoRepository productoRepository,
                               OrdenServicioRepository ordenServicioRepository) {
        this.ventaRepository    = ventaRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.ordenServicioRepository = ordenServicioRepository;
    }
 
    // â”€â”€ Utilidades de fecha â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
 
    /**
     * Ventas que alimentan los reportes agregados del negocio.
     *
     * <p>Excluye las ventas de tipo TALLER porque sus órdenes se reportan aparte en
     * {@link #getReportesServicios(String)}; sumarlas aquí también duplicaría los ingresos
     * y el historial de cada taller. El historial personal del cliente sí las muestra.
     */
    private List<Venta> ventasActivas() {
        return ventaRepository.findAll().stream()
                .filter(v -> !"ANULADA".equals(v.getEstado()) && !"CANCELADA".equals(v.getEstado()))
                .filter(v -> !"TALLER".equals(v.getTipoVenta()))
                .collect(Collectors.toList());
    }
 
    // â”€â”€ DASHBOARD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Override
    public DashboardDTO getDashboard() {
        DashboardDTO dto = new DashboardDTO();
        List<Venta> activas = ventasActivas();
 
        // Total ventas dÃ­a
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
 
        // Producto mÃ¡s vendido
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
                .map(Map.Entry::getKey).orElse("â€”"));
 
        // Cliente mÃ¡s importante (por total gastado)
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
            usuarioRepository.findById(topClienteId)
                    .ifPresent(c -> dto.setClienteMasImportante(c.getNombre()));
        } else {
            dto.setClienteMasImportante("â€”");
        }
 
        // MÃ©todo de pago mÃ¡s usado
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
                .map(Map.Entry::getKey).orElse("â€”"));
 
        // VariaciÃ³n semana
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
 
    // â”€â”€ INVENTARIO CLIENTES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Override
    public InventarioClientesDTO getInventarioClientes() {
        InventarioClientesDTO dto = new InventarioClientesDTO();
        List<Usuario> clientes = usuarioRepository.findByRol("CLIENTE");
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
 
    private ClienteReporteDTO buildClienteReporteDTO(Usuario c, List<Venta> activas) {
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
            dto.setUltimaCompra("â€”");
        }
 
        // MÃ©todo de pago mÃ¡s usado
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
                .map(Map.Entry::getKey).orElse("â€”"));
 
        // ClasificaciÃ³n
        long n = dto.getTotalCompras();
        dto.setClasificacion(n == 0 ? "NUEVO" : n >= 10 ? "FRECUENTE" : "OCASIONAL");
 
        return dto;
    }
 
    // â”€â”€ REPORTES CLIENTES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Override
    public ReporteClientesDTO getReportesClientes(String periodo) {
        ReporteClientesDTO dto = new ReporteClientesDTO();
        dto.setPeriodo(periodo);
        List<Venta> activas = ventasActivas();
        List<Usuario> clientes = usuarioRepository.findByRol("CLIENTE");
 
        LocalDateTime inicio = inicioPeriodo(periodo);
        List<Venta> enPeriodo = activas.stream()
                .filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicio))
                .collect(Collectors.toList());
 
        dto.setTotalRecaudado(enPeriodo.stream()
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0).sum());
 
        // Cliente mÃ¡s comprÃ³ hoy
        Map<String, Double> gastoDia = new HashMap<>();
        activas.stream().filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioDia()))
                .forEach(v -> gastoDia.merge(v.getClienteId(), v.getTotal() == null ? 0.0 : v.getTotal(), Double::sum));
        String topHoyId = gastoDia.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        dto.setClienteMasComproHoy(topHoyId != null ?
                usuarioRepository.findById(topHoyId).map(Usuario::getNombre).orElse("â€”") : "â€”");
 
        // Cliente mÃ¡s comprÃ³ semana
        Map<String, Double> gastoSemana = new HashMap<>();
        activas.stream().filter(v -> v.getFecha() != null && v.getFecha().isAfter(inicioSemana()))
                .forEach(v -> gastoSemana.merge(v.getClienteId(), v.getTotal() == null ? 0.0 : v.getTotal(), Double::sum));
        String topSemanaId = gastoSemana.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        dto.setClienteMasComproSemana(topSemanaId != null ?
                usuarioRepository.findById(topSemanaId).map(Usuario::getNombre).orElse("â€”") : "â€”");
 
        // Ranking top 10
        dto.setRankingClientes(clientes.stream()
                .map(c -> buildClienteReporteDTO(c, enPeriodo))
                .sorted(Comparator.comparingDouble(ClienteReporteDTO::getTotalGastado).reversed())
                .limit(10)
                .collect(Collectors.toList()));
 
        // Clientes inactivos (sin comprar en 30 dÃ­as)
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
 
        // Ventas por dÃ­a (Ãºltimos 7 dÃ­as) para grÃ¡fica
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
 
    // â”€â”€ HISTORIAL CLIENTE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Override
    public HistorialClienteDTO getHistorialCliente(String clienteId) {
        HistorialClienteDTO dto = new HistorialClienteDTO();
        dto.setClienteId(clienteId);
        usuarioRepository.findById(clienteId).ifPresent(c -> dto.setNombre(c.getNombre()));
 
        List<VentaResumenDTO> compras = ventaRepository.findByClienteId(clienteId).stream()
                .filter(v -> !"ANULADA".equals(v.getEstado()))
                .sorted(Comparator.comparing(Venta::getFecha, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(v -> {
                    VentaResumenDTO r = new VentaResumenDTO();
                    r.setId(v.getId());
                    r.setFecha(v.getFecha() != null ?
                            v.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "â€”");
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
 
    // â”€â”€ INVENTARIO PRODUCTOS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
 
    // â”€â”€ REPORTES PRODUCTOS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
 
        // MÃ¡s vendidos
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
 
        // Ventas por dÃ­a (Ãºltimos 7 dÃ­as)
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
 
        // Unidades por categorÃ­a
        Map<String, Long> porCategoria = new LinkedHashMap<>();
        for (ProductoReporteDTO p : lista) {
            if (p.getCategoria() != null) {
                porCategoria.merge(p.getCategoria(), p.getUnidadesVendidas(), Long::sum);
            }
        }
        dto.setUnidadesPorCategoria(porCategoria);
 
        return dto;
    }
 
    // ─── SERVICIOS DE TALLER ──────────────────────────────────────────────────
    /**
     * Seguimiento de los servicios de taller: en qué estado están las órdenes, cuánto
     * entra por ellos, qué talleres prefieren los clientes y qué repuestos llevan más.
     *
     * <p>Se trabaja sobre OrdenServicio y no sobre Venta porque el estado real de la
     * orden vive ahí; la venta queda en PAGADA desde el momento del cobro y no refleja
     * el avance del trabajo. Las ventas tipo TALLER se excluyen de los reportes de
     * producto y de clientes para no sumar dos veces los mismos ingresos.
     */
    @Override
    public ReporteServiciosDTO getReportesServicios(String periodo) {
        LocalDateTime desde = inicioPeriodo(periodo);

        List<OrdenServicio> ordenes = ordenServicioRepository.findAll().stream()
                // Reporte del flujo de servicio V1: las órdenes del marketplace tienen estados propios.
                .filter(o -> !EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .filter(o -> o.getFechaCreacion() != null && !o.getFechaCreacion().isBefore(desde))
                .collect(Collectors.toList());

        long canceladas = ordenes.stream().filter(o -> "CANCELADA".equals(o.getEstado())).count();
        List<OrdenServicio> vigentes = ordenes.stream()
                .filter(o -> !"CANCELADA".equals(o.getEstado()))
                .collect(Collectors.toList());

        long finalizadas = vigentes.stream().filter(o -> "FINALIZADA".equals(o.getEstado())).count();
        long enProceso = vigentes.stream().filter(o -> "EN_PROCESO".equals(o.getEstado())).count();

        // Una orden cuenta como facturada desde que se cobra, sin importar si el
        // taller ya terminó el trabajo.
        List<OrdenServicio> facturadas = vigentes.stream()
                .filter(o -> ESTADOS_COBRADOS.contains(o.getEstado()))
                .collect(Collectors.toList());
        double ingresos = facturadas.stream()
                .mapToDouble(o -> o.getTotal() != null ? o.getTotal() : 0.0)
                .sum();

        ReporteServiciosDTO dto = new ReporteServiciosDTO();
        dto.setTotalOrdenes(ordenes.size());
        dto.setOrdenesFinalizadas(finalizadas);
        dto.setOrdenesEnProceso(enProceso);
        dto.setOrdenesCanceladas(canceladas);
        dto.setIngresosTotales(redondear(ingresos));
        dto.setOrdenesFacturadas(facturadas.size());
        // El ticket se calcula sobre las órdenes cobradas: dividir entre todas las
        // vigentes arrastraría cotizaciones que nunca se facturaron.
        dto.setTicketPromedio(facturadas.isEmpty() ? 0 : redondear(ingresos / facturadas.size()));

        dto.setTalleresPreferidos(rankingTalleres(vigentes, vigentes.size()));
        dto.setProductosMasSolicitados(rankingProductos(vigentes));

        LocalDateTime ahora = LocalDateTime.now();
        dto.setCitasAgendadas(ordenes.stream()
                .filter(o -> o.getFechaProgramada() != null && o.getFechaProgramada().isAfter(ahora))
                .count());
        dto.setCitasPendientes(vigentes.stream()
                .filter(o -> ESTADOS_COBRADOS.contains(o.getEstado())
                        && !"EN_PROCESO".equals(o.getEstado())
                        && !"FINALIZADA".equals(o.getEstado()))
                .count());
        dto.setOrdenesRetrasadas(vigentes.stream()
                .filter(o -> OrdenServicioDTO.estaRetrasada(
                        o.getEstado(), o.getFechaCreacion(), o.getFechaPago(), o.getFechaInicio()))
                .count());

        return dto;
    }

    private List<ReporteServiciosDTO.TallerRankingDTO> rankingTalleres(
            List<OrdenServicio> ordenes, long totalVigentes) {

        Map<String, ReporteServiciosDTO.TallerRankingDTO> porTaller = new LinkedHashMap<>();
        Map<String, Long> finalizadasPorTaller = new HashMap<>();

        for (OrdenServicio o : ordenes) {
            if (o.getTallerId() == null) continue;
            ReporteServiciosDTO.TallerRankingDTO r = porTaller.computeIfAbsent(o.getTallerId(), id ->
                    new ReporteServiciosDTO.TallerRankingDTO(id, o.getTallerNombre(), 0, 0, 0));
            // El nombre se denormaliza en la orden; si cambia en el taller, se actualiza.
            if (o.getTallerNombre() != null) {
                r.setNombre(o.getTallerNombre());
            }
            r.setOrdenes(r.getOrdenes() + 1);
            if ("FINALIZADA".equals(o.getEstado())) {
                finalizadasPorTaller.merge(o.getTallerId(), 1L, Long::sum);
            }
        }

        List<ReporteServiciosDTO.TallerRankingDTO> lista = new ArrayList<>(porTaller.values());
        for (ReporteServiciosDTO.TallerRankingDTO r : lista) {
            r.setFinalizadas(finalizadasPorTaller.getOrDefault(r.getTallerId(), 0L));
            r.setParticipacion(totalVigentes == 0 ? 0
                    : redondear((r.getOrdenes() * 100.0) / totalVigentes));
        }
        lista.sort(Comparator.comparingLong(ReporteServiciosDTO.TallerRankingDTO::getOrdenes).reversed());
        return lista;
    }

    private List<ReporteServiciosDTO.ProductoRankingDTO> rankingProductos(List<OrdenServicio> ordenes) {
        Map<String, ReporteServiciosDTO.ProductoRankingDTO> porProducto = new LinkedHashMap<>();

        for (OrdenServicio o : ordenes) {
            if (o.getProductoId() == null) continue;
            ReporteServiciosDTO.ProductoRankingDTO r = porProducto.computeIfAbsent(o.getProductoId(), id ->
                    new ReporteServiciosDTO.ProductoRankingDTO(id, o.getProductoNombre(), 0, 0));
            if (o.getProductoNombre() != null) {
                r.setNombre(o.getProductoNombre());
            }
            r.setUnidades(r.getUnidades() + 1);
            r.setOrdenes(r.getOrdenes() + 1);
        }

        List<ReporteServiciosDTO.ProductoRankingDTO> lista = new ArrayList<>(porProducto.values());
        lista.sort(Comparator.comparingLong(ReporteServiciosDTO.ProductoRankingDTO::getUnidades).reversed());
        return lista;
    }
 
    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}