package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wompi permite reintentar con la MISMA referencia después de un rechazo: el pago pasa
 * PENDIENTE → RECHAZADO → (reintento aprobado) → aplicado. Aquí el inventario, los
 * movimientos, los pedidos, los repartos y la fila del pago son reales sobre datos en
 * memoria; solo el SQL de {@code reclamar}/{@code marcarRechazado} se emula, y los
 * estados que acepta {@code reclamar} se leen de su @Query real, de modo que si alguien
 * quita RECHAZADO de esa consulta estas pruebas fallan.
 */
class PagoRechazadoLuegoAprobadoTest {

    private static final Pattern ESTADOS_TOMABLES = Pattern.compile("estado in \\(([^)]*)\\)");

    private final Map<String, Producto> productos = new HashMap<>();
    private final List<MovimientoInventario> movimientos = new ArrayList<>();
    private final List<PedidoTienda> pedidos = new ArrayList<>();
    private final List<RepartoPago> repartos = new ArrayList<>();

    /** La fila del pago en la "base de datos". Cada confirmar() recibe una copia, como al cargarla con JPA. */
    private PagoEntity fila;
    private OrdenServicio orden;
    private PagoRepository pagoRepository;
    private PagoMarketplaceService service;
    private Set<String> estadosTomables;

    private static Producto producto(String id, String tiendaId, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Repuesto " + id);
        p.setStockActual(stock);
        p.setTiendaId(tiendaId);
        p.setTipo("STOCK");
        return p;
    }

    private static CotizacionItem item(String productoId, String tiendaId, int cantidad) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).productoId(productoId)
                .productoNombre("Repuesto " + productoId).tiendaId(tiendaId).tiendaNombre("Tienda " + tiendaId)
                .cantidad(cantidad).subtotal(20_000.0 * cantidad).disponibilidad("DISPONIBLE").build();
    }

    /** Estados que el reclamar() real acepta, leídos de su @Query. */
    private static Set<String> estadosDelReclamarActual() throws NoSuchMethodException {
        String jpql = PagoRepository.class.getMethod("reclamar", Long.class, String.class)
                .getAnnotation(Query.class).value();
        Matcher m = ESTADOS_TOMABLES.matcher(jpql);
        assertTrue(m.find(), "No se reconoce la consulta de reclamar: " + jpql);
        Set<String> estados = new java.util.HashSet<>();
        for (String e : m.group(1).split(",")) estados.add(e.trim().replace("'", ""));
        return estados;
    }

    private static PagoEntity copia(PagoEntity o) {
        PagoEntity p = new PagoEntity();
        p.setId(o.getId());
        p.setConcepto(o.getConcepto());
        p.setOrdenServicioId(o.getOrdenServicioId());
        p.setCotizacionId(o.getCotizacionId());
        p.setUsuarioId(o.getUsuarioId());
        p.setReferencia(o.getReferencia());
        p.setMonto(o.getMonto());
        p.setEstado(o.getEstado());
        p.setTransactionId(o.getTransactionId());
        return p;
    }

    @BeforeEach
    void setUp() throws Exception {
        estadosTomables = estadosDelReclamarActual();
        prepararServicio();
    }

    private void prepararServicio() {
        productos.put("filtro", producto("filtro", "s1", 5));
        productos.put("pastilla", producto("pastilla", "s1", 5));
        productos.put("freno", producto("freno", "s2", 4));

        ProductoService productoService = mock(ProductoService.class);
        when(productoService.obtenerPorId(anyString())).thenAnswer(i -> productos.get((String) i.getArgument(0)));
        when(productoService.descontarStock(anyString(), anyInt())).thenAnswer(i -> {
            Producto p = productos.get((String) i.getArgument(0));
            int cantidad = i.getArgument(1);
            if (p.getStockActual() < cantidad) throw new StockInsuficienteException(p.getNombre(), p.getStockActual());
            p.setStockActual(p.getStockActual() - cantidad);
            return p;
        });
        when(productoService.incrementarStock(anyString(), anyInt())).thenAnswer(i -> {
            Producto p = productos.get((String) i.getArgument(0));
            p.setStockActual(p.getStockActual() + (int) i.getArgument(1));
            return p;
        });

        MovimientoInventarioRepository movimientoRepository = mock(MovimientoInventarioRepository.class);
        when(movimientoRepository.save(any())).thenAnswer(i -> {
            movimientos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(movimientoRepository.existsByProductoIdAndTipoAndReferencia(anyString(), anyString(), anyString()))
                .thenAnswer(i -> movimientos.stream().anyMatch(m -> m.getProductoId().equals(i.getArgument(0))
                        && m.getTipo().equals(i.getArgument(1)) && i.getArgument(2).equals(m.getReferencia())));
        MovimientoInventarioService movimientoService = new MovimientoInventarioServiceImpl(movimientoRepository, productoService);

        pagoRepository = mock(PagoRepository.class);
        when(pagoRepository.findByReferencia(anyString()))
                .thenAnswer(i -> i.getArgument(0).equals(fila.getReferencia()) ? Optional.of(copia(fila)) : Optional.empty());
        // UPDATE ... WHERE estado IN (...): atómico sobre la fila; los estados salen del @Query real
        when(pagoRepository.reclamar(anyLong(), anyString())).thenAnswer(i -> {
            if (!estadosTomables.contains(fila.getEstado())) return 0;
            fila.setEstado("PROCESANDO");
            fila.setTransactionId(i.getArgument(1));
            return 1;
        });
        when(pagoRepository.marcarRechazado(anyLong(), anyString())).thenAnswer(i -> {
            if (!"PENDIENTE".equals(fila.getEstado())) return 0;
            fila.setEstado("RECHAZADO");
            fila.setTransactionId(i.getArgument(1));
            return 1;
        });
        when(pagoRepository.save(any())).thenAnswer(i -> {
            PagoEntity p = i.getArgument(0);
            fila = copia(p);
            return p;
        });

        OrdenServicioRepository ordenRepository = mock(OrdenServicioRepository.class);
        CotizacionRepository cotizacionRepository = mock(CotizacionRepository.class);
        PedidoTiendaRepository pedidoRepository = mock(PedidoTiendaRepository.class);
        when(pedidoRepository.save(any())).thenAnswer(i -> {
            PedidoTienda p = i.getArgument(0);
            p.setId("p" + (pedidos.size() + 1));
            pedidos.add(p);
            return p;
        });
        RepartoPagoRepository repartoRepository = mock(RepartoPagoRepository.class);
        when(repartoRepository.save(any())).thenAnswer(i -> {
            repartos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        TallerRepository tallerRepository = mock(TallerRepository.class);
        when(tallerRepository.findById("t1")).thenReturn(Optional.of(Taller.builder().id("t1").nombre("AutoMax").build()));
        TransicionOrdenService transiciones = mock(TransicionOrdenService.class);
        when(transiciones.transicionar(any(), anyString(), any(), anyString(), any(), any())).thenAnswer(i -> {
            OrdenServicio o = i.getArgument(0);
            o.setEstado(i.getArgument(1));
            return o;
        });

        service = new PagoMarketplaceService(mock(PagoService.class), pagoRepository, ordenRepository, cotizacionRepository,
                pedidoRepository, repartoRepository, mock(ReembolsoRepository.class), tallerRepository,
                productoService, movimientoService, movimientoRepository, mock(UsuarioService.class), transiciones,
                mock(CotizacionService.class), mock(AvisosSolicitudServicio.class));

        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE).estado(EstadoOrden.APROBADA)
                .tallerId("t1").usuarioId("u1").cotizacionId("c1").build();
        when(ordenRepository.findById("o1")).thenAnswer(i -> Optional.of(orden));
        Cotizacion cotizacion = Cotizacion.builder().id("c1").ordenId("o1").version(1).estado(Cotizacion.APROBADA).ivaPct(19.0)
                .items(new ArrayList<>(List.of(
                        item("filtro", "s1", 2),
                        item("pastilla", "s1", 1),
                        item("freno", "s2", 1))))
                .totalAPagar(100_000.0).vigenciaHasta(LocalDateTime.now().plusDays(1)).build();
        when(cotizacionRepository.findById("c1")).thenReturn(Optional.of(cotizacion));

        fila = new PagoEntity();
        fila.setId(10L);
        fila.setConcepto(PagoMarketplaceService.COTIZACION);
        fila.setOrdenServicioId("o1");
        fila.setCotizacionId("c1");
        fila.setUsuarioId("u1");
        fila.setReferencia("DRV-REF");
        fila.setMonto(100_000.0);
        fila.setEstado("PENDIENTE");
    }

    /** Intento 1: Wompi lo declina. Intento 2: misma referencia, Wompi lo aprueba. */
    private void rechazadoYLuegoAprobado() {
        // Flujo de PagoService.marcarSiRechazado
        assertEquals(1, pagoRepository.marcarRechazado(fila.getId(), "txn-declinada"));
        assertEquals("RECHAZADO", fila.getEstado());
        // Flujo de PagoService.confirmarPagoAprobado: carga el pago por referencia y confirma
        service.confirmar(pagoRepository.findByReferencia("DRV-REF").orElseThrow(), "txn-aprobada");
    }

    private List<MovimientoInventario> salidas(String productoId) {
        return movimientos.stream().filter(m -> "SALIDA".equals(m.getTipo()) && productoId.equals(m.getProductoId())).toList();
    }

    private void assertAplicadoUnaSolaVez() {
        // Stock: bajó una vez
        assertEquals(3, productos.get("filtro").getStockActual());
        assertEquals(4, productos.get("pastilla").getStockActual());
        assertEquals(3, productos.get("freno").getStockActual());
        // Una SALIDA por producto, con la referencia PAGO-<referencia>
        for (String id : List.of("filtro", "pastilla", "freno")) {
            List<MovimientoInventario> s = salidas(id);
            assertEquals(1, s.size(), "SALIDA de " + id);
            assertEquals("PAGO-DRV-REF", s.get(0).getReferencia());
        }
        assertEquals(3, movimientos.size());
        // Un pedido por tienda
        assertEquals(2, pedidos.size());
        assertEquals(List.of("s1", "s2"), pedidos.stream().map(PedidoTienda::getTiendaId).sorted().toList());
        // Reparto: una línea por tienda + el resto al taller
        assertEquals(3, repartos.size());
        assertEquals(2, repartos.stream().filter(r -> RepartoPago.TIENDA.equals(r.getBeneficiarioTipo())).count());
        assertEquals(1, repartos.stream().filter(r -> RepartoPago.TALLER.equals(r.getBeneficiarioTipo())).count());
        assertEquals(EstadoOrden.REPUESTOS_PEDIDOS, orden.getEstado());
        assertEquals("PAGADO", fila.getEstado());
        assertEquals("txn-aprobada", fila.getTransactionId());
    }

    @Test
    void elReclamarRealTomaTambienLosPagosRechazados() {
        assertEquals(Set.of("PENDIENTE", "RECHAZADO"), estadosTomables);
    }

    @Test
    void rechazadoYLuegoAprobadoConLaMismaReferenciaSeAplicaUnaVez() {
        rechazadoYLuegoAprobado();
        assertAplicadoUnaSolaVez();
    }

    @Test
    void aplicarloUnaSegundaVezNoRepiteStockPedidosNiReparto() {
        rechazadoYLuegoAprobado();

        // Webhook + redirección + "Ya pagué": llegan de nuevo con el pago ya PAGADO
        service.confirmar(copia(fila), "txn-aprobada");
        service.confirmar(copia(fila), "txn-aprobada");

        assertAplicadoUnaSolaVez();
    }

    @Test
    void sinRechazoPrevioTambienSeAplicaUnaSolaVez() {
        service.confirmar(copia(fila), "txn-aprobada");
        service.confirmar(copia(fila), "txn-aprobada");
        assertAplicadoUnaSolaVez();
    }

    /** Documenta el fallo del código anterior: reclamar solo tomaba PENDIENTE. */
    @Test
    void conElReclamarAnteriorElReintentoAprobadoSePerdia() {
        estadosTomables = Set.of("PENDIENTE");

        rechazadoYLuegoAprobado();

        // El cliente pagó (Wompi APPROVED) pero DriveMaster no aplicó nada
        assertEquals("RECHAZADO", fila.getEstado());
        assertEquals(5, productos.get("filtro").getStockActual());
        assertTrue(movimientos.isEmpty());
        assertTrue(pedidos.isEmpty());
        assertTrue(repartos.isEmpty());
        assertEquals(EstadoOrden.APROBADA, orden.getEstado());
    }
}
