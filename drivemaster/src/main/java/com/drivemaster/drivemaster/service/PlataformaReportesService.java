package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ComisionGanada;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ClienteCompras;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ClientesConMasCompras;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ComisionNeta;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.MesComision;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.MesVenta;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ProductoMasVendido;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ProductosMasVendidos;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.SaldoAFavor;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.SaldosAFavor;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ServiciosPorTaller;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.TallerServicios;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.TiendaVentas;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.VentasPorTienda;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reportes de la plataforma sobre pedidos de tienda, pagos y órdenes de servicio (no sobre Venta). Son de solo
 * lectura y solo SUPERADMIN: el acceso lo define SecurityConfig (/api/plataforma/**).
 */
@Service
public class PlataformaReportesService {

    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int MESES_POR_DEFECTO = 12;
    static final int MESES_MAXIMO = 24;
    static final int LIMITE_POR_DEFECTO = 10;
    static final int LIMITE_MAXIMO = 50;

    private final MongoTemplate mongo;
    private final RepartoPagoRepository repartoRepository;
    private final ReembolsoRepository reembolsoRepository;
    private final ParametrosMarketplace parametros;
    // Opcional para no cambiar el constructor que usan las pruebas: sin él, el dinero que pasó se muestra en 0
    private com.drivemaster.drivemaster.repository.mysql.PagoRepository pagoRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPagoRepository(com.drivemaster.drivemaster.repository.mysql.PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    public PlataformaReportesService(MongoTemplate mongo, RepartoPagoRepository repartoRepository,
                                     ReembolsoRepository reembolsoRepository, ParametrosMarketplace parametros) {
        this.mongo = mongo;
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.parametros = parametros;
    }

    // ─── Ventas por tienda y por mes ─────────────────────────────────────────

    public VentasPorTienda ventasPorTienda(int meses) {
        return ventasPorTienda(meses, YearMonth.now(ZONA));
    }

    VentasPorTienda ventasPorTienda(int meses, YearMonth hasta) {
        int m = meses(meses);
        List<String> lista = mesesHasta(hasta, m);
        Date desde = inicio(hasta.minusMonths(m - 1L));

        List<Document> filas = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO)
                        .and("fechaCreacion").gte(desde)),
                Aggregation.project("tiendaId", "tiendaNombre", "total")
                        .and(DateOperators.dateOf("fechaCreacion").withTimezone(DateOperators.Timezone.valueOf(ZONA.getId()))
                                .toString("%Y-%m")).as("mes"),
                Aggregation.group("tiendaId", "mes").first("tiendaNombre").as("tienda").count().as("pedidos")
                        .sum("total").as("ventas")), "pedidos_tienda", Document.class).getMappedResults();

        Map<String, String> nombres = new LinkedHashMap<>();
        Map<String, Map<String, MesVenta>> porTienda = new LinkedHashMap<>();
        for (Document d : filas) {
            Document id = (Document) d.get("_id");
            String tiendaId = String.valueOf(id.get("tiendaId"));
            String mes = String.valueOf(id.get("mes"));
            nombres.putIfAbsent(tiendaId, d.getString("tienda"));
            porTienda.computeIfAbsent(tiendaId, k -> new HashMap<>())
                    .put(mes, new MesVenta(mes, numero(d.get("pedidos")), decimal(d.get("ventas"))));
        }

        List<TiendaVentas> tiendas = new ArrayList<>();
        Map<String, long[]> pedidosMes = new HashMap<>();
        Map<String, double[]> ventasMes = new HashMap<>();
        for (Map.Entry<String, Map<String, MesVenta>> e : porTienda.entrySet()) {
            List<MesVenta> alineados = new ArrayList<>();
            long pedidos = 0;
            double ventas = 0;
            for (String mes : lista) {
                MesVenta v = e.getValue().getOrDefault(mes, new MesVenta(mes, 0, 0));
                alineados.add(v);
                pedidos += v.pedidos();
                ventas += v.ventas();
                pedidosMes.computeIfAbsent(mes, k -> new long[1])[0] += v.pedidos();
                ventasMes.computeIfAbsent(mes, k -> new double[1])[0] += v.ventas();
            }
            tiendas.add(new TiendaVentas(e.getKey(), nombres.get(e.getKey()), alineados, pedidos, ventas));
        }
        tiendas.sort(Comparator.comparingDouble(TiendaVentas::ventas).reversed());
        List<MesVenta> totales = lista.stream().map(mes -> new MesVenta(mes,
                pedidosMes.getOrDefault(mes, new long[1])[0], ventasMes.getOrDefault(mes, new double[1])[0])).toList();
        return new VentasPorTienda(lista, tiendas, totales);
    }

    // ─── Productos más vendidos ──────────────────────────────────────────────

    public ProductosMasVendidos productosMasVendidos(int meses, int limite) {
        return productosMasVendidos(meses, limite, YearMonth.now(ZONA));
    }

    ProductosMasVendidos productosMasVendidos(int meses, int limite, YearMonth hasta) {
        int m = meses(meses);
        Criteria vendido = Criteria.where("estado").nin(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO)
                .and("fechaCreacion").gte(inicio(hasta.minusMonths(m - 1L)));
        List<ProductoMasVendido> productos = agrupar("pedidos_tienda", Aggregation.match(vendido),
                Aggregation.unwind("items"),
                Aggregation.match(Criteria.where("items.productoId").ne(null)),
                Aggregation.group("items.productoId").first("items.productoNombre").as("nombre")
                        .first("tiendaNombre").as("tienda")
                        .sum("items.cantidad").as("unidades").sum("items.subtotal").as("ventas"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "unidades")),
                Aggregation.limit(limite(limite))).stream()
                .map(d -> new ProductoMasVendido(String.valueOf(d.get("_id")), texto(d.get("nombre")), texto(d.get("tienda")),
                        numero(d.get("unidades")), decimal(d.get("ventas"))))
                .toList();
        return new ProductosMasVendidos(m, productos);
    }

    // ─── Clientes con más compras ────────────────────────────────────────────

    public ClientesConMasCompras clientesConMasCompras(int meses, int limite) {
        return clientesConMasCompras(meses, limite, YearMonth.now(ZONA));
    }

    ClientesConMasCompras clientesConMasCompras(int meses, int limite, YearMonth hasta) {
        int m = meses(meses);
        List<Document> filas = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("estado").is(CompraCatalogo.PAGADA)
                        .and("fechaCreacion").gte(inicio(hasta.minusMonths(m - 1L)))),
                Aggregation.group("clienteId").count().as("compras").sum("total").as("total"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "compras").and(Sort.by(Sort.Direction.DESC, "total"))),
                Aggregation.limit(limite(limite))), CompraCatalogo.class, Document.class).getMappedResults();

        List<String> ids = filas.stream().map(d -> String.valueOf(d.get("_id"))).toList();
        Map<String, Usuario> usuarios = ids.isEmpty() ? Map.of()
                : mongo.find(Query.query(Criteria.where("id").in(ids)), Usuario.class).stream()
                        .collect(Collectors.toMap(Usuario::getId, u -> u, (a, b) -> a));
        List<ClienteCompras> clientes = filas.stream().map(d -> {
            String id = String.valueOf(d.get("_id"));
            Usuario u = usuarios.get(id);
            return new ClienteCompras(id, u != null ? abreviar(u.getNombre()) : null, u != null ? u.getCiudad() : null,
                    numero(d.get("compras")), decimal(d.get("total")));
        }).toList();
        return new ClientesConMasCompras(m, clientes);
    }

    // ─── Servicios por taller ────────────────────────────────────────────────

    public ServiciosPorTaller serviciosPorTaller() {
        List<Document> filas = mongo.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("flujo").is(EstadoOrden.FLUJO_MARKETPLACE).and("tallerId").ne(null)),
                Aggregation.group("tallerId", "estado").count().as("n")), OrdenServicio.class, Document.class).getMappedResults();

        Map<String, long[]> cuentas = new LinkedHashMap<>(); // [total, entregadas, enCurso, canceladas]
        for (Document d : filas) {
            Document id = (Document) d.get("_id");
            String tallerId = String.valueOf(id.get("tallerId"));
            String estado = String.valueOf(id.get("estado"));
            long n = numero(d.get("n"));
            long[] c = cuentas.computeIfAbsent(tallerId, k -> new long[4]);
            c[0] += n;
            if (EstadoOrden.ENTREGADA.equals(estado)) c[1] += n;
            else if (EstadoOrden.CANCELADA.equals(estado) || EstadoOrden.RECHAZADA.equals(estado)) c[3] += n;
            else c[2] += n;
        }
        Map<String, String> nombres = cuentas.isEmpty() ? Map.of()
                : mongo.find(Query.query(Criteria.where("id").in(cuentas.keySet())), Taller.class).stream()
                        .collect(Collectors.toMap(Taller::getId, t -> t.getNombre() != null ? t.getNombre() : "", (a, b) -> a));
        Map<String, Double> movido = new HashMap<>();
        for (Object[] fila : repartoRepository.sumaRepartidaPorTaller()) {
            movido.put(String.valueOf(fila[0]), fila[1] instanceof Number n ? n.doubleValue() : 0.0);
        }
        List<TallerServicios> talleres = cuentas.entrySet().stream()
                .map(e -> new TallerServicios(e.getKey(), nombres.get(e.getKey()), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3], movido.getOrDefault(e.getKey(), 0.0)))
                .sorted(Comparator.comparingLong(TallerServicios::total).reversed())
                .toList();
        return new ServiciosPorTaller(talleres);
    }

    // ─── Comisión registrada neta ────────────────────────────────────────────

    public ComisionNeta comisionNeta(int meses) {
        return comisionNeta(meses, YearMonth.now(ZONA), LocalDateTime.now());
    }

    ComisionNeta comisionNeta(int meses, YearMonth hasta, LocalDateTime ahora) {
        int m = meses(meses);
        List<String> lista = mesesHasta(hasta, m);
        // Inicio del primer mes en hora de Colombia, expresado en la hora del servidor (así se guardan las fechas)
        LocalDateTime desde = hasta.minusMonths(m - 1L).atDay(1).atStartOfDay(ZONA)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        Map<String, double[]> porMes = new TreeMap<>(); // [registrada, anulada, cobrada]
        lista.forEach(mes -> porMes.put(mes, new double[3]));
        Map<String, Double> porConcepto = new TreeMap<>();
        for (RepartoPago r : repartoRepository.comisionDesde(desde)) {
            // La retenida no es informativa: su cifra sale de las líneas de los negocios
            if (r.getFechaCreacion() == null || r.getMonto() == null || RepartoPago.RETENIDA.equals(r.getEstado())) continue;
            String mes = mesEnColombia(r.getFechaCreacion()).toString();
            double[] acumulado = porMes.get(mes);
            if (acumulado == null) continue;
            if (r.getMonto() >= 0) acumulado[0] += r.getMonto();
            else acumulado[1] += -r.getMonto();
            porConcepto.merge(r.getConcepto() != null ? r.getConcepto() : "OTRO", r.getMonto(), Double::sum);
        }
        // La cobrada cuenta en el mes en que se pagó el neto
        for (RepartoPago r : repartoRepository.comisionCobradaDesde(desde)) {
            LocalDateTime pagada = r.getFechaLiquidacion() != null ? r.getFechaLiquidacion() : r.getFechaCreacion();
            double[] acumulado = pagada == null ? null : porMes.get(mesEnColombia(pagada).toString());
            if (acumulado != null) acumulado[2] += r.getComisionRetenida();
        }
        List<MesComision> filas = porMes.entrySet().stream()
                .map(e -> new MesComision(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[0] - e.getValue()[1], e.getValue()[2]))
                .toList();
        double registrada = filas.stream().mapToDouble(MesComision::registrada).sum();
        double anulada = filas.stream().mapToDouble(MesComision::anulada).sum();
        return new ComisionNeta(ahora, filas, registrada, anulada, registrada - anulada, porConcepto,
                DashboardPlataformaDTO.ComisionRetenida.de(repartoRepository.comisionRetenidaPorEstado()),
                parametros.comisionRetenida(), saldosAFavor(),
                pagoRepository == null ? 0 : pagoRepository.sumaMovidaMarketplaceDesde(desde));
    }

    // ─── Comisión ganada en un periodo ───────────────────────────────────────

    /** La comisión del periodo; sin fechas, el mes en curso (hora de Colombia). */
    public ComisionGanada comisionGanada(RangoFechas rango) {
        return comisionGanada(rango, RangoFechas.hoy());
    }

    /**
     * Sale de las mismas líneas que Liquidaciones: la línea PLATAFORMA RETENIDA de cada pago es la suma de la comisión
     * retenida en sus líneas de negocio (ComisionPlataforma.registrar) y cada reembolso la anula con una línea negativa
     * (ComisionPlataforma.anular). Así, ganada = suma de la columna "Comisión" de Liquidaciones sin las líneas anuladas.
     */
    ComisionGanada comisionGanada(RangoFechas rango, java.time.LocalDate hoy) {
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime desde = rango != null && rango.desde() != null ? rango.desde() : inicioMes;
        LocalDateTime antesDe = rango != null && rango.antesDe() != null ? rango.antesDe()
                : (rango != null && rango.desde() != null ? hoy.plusDays(1).atStartOfDay() : inicioMes.plusMonths(1));
        double retenida = 0, anulada = 0, informativa = 0;
        for (RepartoPago r : repartoRepository.comisionEntre(desde, antesDe)) {
            double monto = r.getMonto() == null ? 0 : r.getMonto();
            if (RepartoPago.RETENIDA.equals(r.getEstado())) {
                if (monto >= 0) retenida += monto;
                else anulada += -monto;
            } else if (RepartoPago.INFORMATIVA.equals(r.getEstado())) {
                informativa += monto;
            }
        }
        double liquidada = repartoRepository.comisionCobradaEntre(desde, antesDe).stream()
                .mapToDouble(r -> r.getComisionRetenida() == null ? 0 : r.getComisionRetenida()).sum();
        return new ComisionGanada(desde, antesDe, retenida - anulada, retenida, anulada, liquidada,
                Math.max(0, informativa), parametros.comisionRetenida());
    }

    private SaldosAFavor saldosAFavor() {
        List<SaldoAFavor> items = reembolsoRepository.saldosAFavorPendientes().stream()
                .map(b -> new SaldoAFavor(b.getId(), b.getSaldoAFavor(), b.getMotivo(), b.getReferenciaPago(), b.getFechaCreacion(),
                        b.getSaldoDeudorNombre()))
                .toList();
        return new SaldosAFavor(items.stream().mapToDouble(SaldoAFavor::monto).sum(), items.size(), items);
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Mes calendario en hora de Colombia (UTC-5) de una fecha guardada en la hora del servidor. Sin esta conversión,
     * un servidor en UTC pasaría al mes siguiente una compra hecha a las 7 p. m. del último día.
     */
    static YearMonth mesEnColombia(LocalDateTime fechaDelServidor) {
        return YearMonth.from(fechaDelServidor.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZONA));
    }

    /** "Ana Pérez Gómez" → "Ana P." */
    static String abreviar(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String[] partes = nombre.trim().split("\\s+");
        return partes.length == 1 ? partes[0] : partes[0] + " " + Character.toUpperCase(partes[1].charAt(0)) + ".";
    }

    static int meses(int pedido) {
        return pedido <= 0 ? MESES_POR_DEFECTO : Math.min(pedido, MESES_MAXIMO);
    }

    static int limite(int pedido) {
        return pedido <= 0 ? LIMITE_POR_DEFECTO : Math.min(pedido, LIMITE_MAXIMO);
    }

    private static List<String> mesesHasta(YearMonth hasta, int cuantos) {
        List<String> r = new ArrayList<>();
        for (int i = cuantos - 1; i >= 0; i--) r.add(hasta.minusMonths(i).toString());
        return r;
    }

    private static Date inicio(YearMonth mes) {
        return Date.from(mes.atDay(1).atStartOfDay(ZONA).toInstant());
    }

    private List<Document> agrupar(String coleccion, AggregationOperation... pasos) {
        return mongo.aggregate(Aggregation.newAggregation(pasos), coleccion, Document.class).getMappedResults();
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static double decimal(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
