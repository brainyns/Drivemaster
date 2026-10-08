package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.catalogo.CompraCotizadaDTO;
import com.drivemaster.drivemaster.dto.catalogo.CompraDTO;
import com.drivemaster.drivemaster.dto.catalogo.CompraRequest;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CompraCatalogoRepository;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.util.CodigoRetiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Compra directa del catálogo con servicios reales y datos en memoria (inventario, movimientos,
 * compras, pedidos, repartos, reembolsos y pagos); solo se simulan Wompi, el correo y Mongo.
 */
class CompraCatalogoServiceTest {

    private static final String CORREO = "cliente@demo.com";

    private final Map<String, Producto> productos = new HashMap<>();
    private final List<MovimientoInventario> movimientos = new ArrayList<>();
    private final Map<String, CompraCatalogo> compras = new LinkedHashMap<>();
    private final Map<String, PedidoTienda> pedidos = new LinkedHashMap<>();
    private final List<RepartoPago> repartos = new ArrayList<>();
    private final List<Reembolso> reembolsos = new ArrayList<>();
    private final Map<Long, PagoEntity> pagos = new HashMap<>();
    private final AtomicLong secuencia = new AtomicLong(100);

    private List<Tienda> tiendas;
    private TiendaRepository tiendaRepository;
    private PagoService pagoService;
    private PagoRepository pagoRepository;
    private PedidoTiendaRepository pedidoRepository;
    private AvisosSolicitudServicio avisos;
    private MongoTemplate mongoTemplate;
    private CompraCatalogoService service;

    private static Tienda tienda(String id, String ciudad, String horario, Double envio) {
        return Tienda.builder().id(id).nombre("Tienda " + id).ciudad(ciudad).direccion("Calle " + id).horario(horario)
                .costoEnvio(envio).estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
    }

    private static Producto producto(String id, String tiendaId, double precio, int stock, String tipo) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setReferencia("REF-" + id);
        p.setTiendaId(tiendaId);
        p.setPrecioVenta(precio);
        p.setStockActual(stock);
        p.setTipo(tipo);
        if ("ENCARGO".equals(tipo)) p.setDiasEntregaEncargo(4);
        return p;
    }

    @BeforeEach
    void setUp() {
        tiendas = new ArrayList<>(List.of(
                tienda("s1", "Bogotá", "L-S 8am-6pm", null),        // solo retiro
                tienda("s2", "Medellín", "L-V 9am-5pm", 8000.0)));  // retiro o envío por 8.000
        productos.put("filtro", producto("filtro", "s1", 20_000, 5, "STOCK"));
        productos.put("pastilla", producto("pastilla", "s2", 30_000, 3, "STOCK"));
        productos.put("disco", producto("disco", "s2", 50_000, 0, "ENCARGO"));

        tiendaRepository = mock(TiendaRepository.class);
        when(tiendaRepository.findAll()).thenAnswer(i -> tiendas);
        CatalogoService catalogo = new CatalogoService(mock(MongoTemplate.class), tiendaRepository, mock(VehiculoService.class));

        // Inventario real sobre un stock en memoria (misma semántica atómica que ProductoServiceImpl)
        ProductoService productoService = mock(ProductoService.class);
        when(productoService.obtenerPorId(anyString())).thenAnswer(i -> {
            Producto p = productos.get((String) i.getArgument(0));
            if (p == null) throw new RuntimeException("Producto no encontrado");
            return p;
        });
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

        CompraCatalogoRepository compraRepository = mock(CompraCatalogoRepository.class);
        when(compraRepository.save(any(CompraCatalogo.class))).thenAnswer(i -> {
            CompraCatalogo c = i.getArgument(0);
            if (c.getId() == null) c.setId("c" + secuencia.incrementAndGet());
            compras.put(c.getId(), c);
            return c;
        });
        when(compraRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(compras.get((String) i.getArgument(0))));
        when(compraRepository.findByClienteIdOrderByFechaCreacionDesc(anyString())).thenAnswer(i -> compras.values().stream()
                .filter(c -> c.getClienteId().equals(i.getArgument(0))).toList());
        doAnswer(i -> compras.remove((String) i.getArgument(0))).when(compraRepository).deleteById(anyString());

        pedidoRepository = mock(PedidoTiendaRepository.class);
        when(pedidoRepository.save(any(PedidoTienda.class))).thenAnswer(i -> {
            PedidoTienda p = i.getArgument(0);
            pedidos.put(p.getId(), p);
            return p;
        });
        when(pedidoRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(pedidos.get((String) i.getArgument(0))));
        when(pedidoRepository.findByCompraIdOrderByFechaCreacionAsc(anyString())).thenAnswer(i -> pedidos.values().stream()
                .filter(p -> i.getArgument(0).equals(p.getCompraId())).toList());
        doAnswer(i -> {
            Iterable<PedidoTienda> lista = i.getArgument(0);
            lista.forEach(p -> pedidos.remove(p.getId()));
            return null;
        }).when(pedidoRepository).deleteAll(anyIterable());

        RepartoPagoRepository repartoRepository = mock(RepartoPagoRepository.class);
        when(repartoRepository.save(any(RepartoPago.class))).thenAnswer(i -> {
            RepartoPago r = i.getArgument(0);
            if (r.getId() == null) r.setId(secuencia.incrementAndGet());
            repartos.add(r);
            return r;
        });
        doAnswer(i -> {
            Iterable<RepartoPago> lista = i.getArgument(0);
            lista.forEach(repartos::remove);
            return null;
        }).when(repartoRepository).deleteAll(anyIterable());
        when(repartoRepository.existsByPagoIdAndBeneficiarioTipo(anyLong(), anyString())).thenAnswer(i -> repartos.stream()
                .anyMatch(r -> i.getArgument(0).equals(r.getPagoId()) && i.getArgument(1).equals(r.getBeneficiarioTipo())));

        ReembolsoRepository reembolsoRepository = mock(ReembolsoRepository.class);
        when(reembolsoRepository.save(any(Reembolso.class))).thenAnswer(i -> {
            reembolsos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(reembolsoRepository.existsByPagoId(anyLong())).thenAnswer(i -> !reembolsos.isEmpty());

        pagoRepository = mock(PagoRepository.class);
        when(pagoRepository.findByReferencia(anyString())).thenAnswer(i -> pagos.values().stream()
                .filter(p -> p.getReferencia().equals(i.getArgument(0))).findFirst());

        pagoService = mock(PagoService.class);
        when(pagoService.crearPagoCatalogo(anyString(), anyDouble(), anyString())).thenAnswer(i -> {
            PagoEntity p = new PagoEntity();
            p.setId(secuencia.incrementAndGet());
            p.setConcepto(PagoMarketplaceService.CATALOGO);
            p.setUsuarioId(i.getArgument(0));
            p.setMonto(Math.round((double) i.getArgument(1) * 100) / 100.0);
            p.setCompraCatalogoId(i.getArgument(2));
            p.setReferencia("DRV-" + i.getArgument(2));
            p.setEstado("PENDIENTE");
            pagos.put(p.getId(), p);
            return Map.<String, Object>of("checkoutUrl", "https://checkout.wompi.co/p/?x=1", "referencia", p.getReferencia(),
                    "monto", Math.round(p.getMonto() * 100), "verificacionManual", false, "pagoSimulado", false);
        });

        UsuarioService usuarioService = mock(UsuarioService.class);
        when(usuarioService.obtenerPorCorreo(CORREO)).thenReturn(Usuario.builder().id("u1").nombre("Ana").correo(CORREO).rol("CLIENTE").build());
        ParametrosMarketplace parametros = mock(ParametrosMarketplace.class);
        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.plazoDespachoDiasHabiles()).thenReturn(2);
        when(parametros.plazoEncargoDiasExtra()).thenReturn(2);
        avisos = mock(AvisosSolicitudServicio.class);
        mongoTemplate = mock(MongoTemplate.class);

        service = new CompraCatalogoService(catalogo, mongoTemplate, compraRepository, pedidoRepository, repartoRepository,
                reembolsoRepository, pagoRepository, pagoService, usuarioService, mock(VehiculoService.class), productoService,
                movimientoService, movimientoRepository, parametros, avisos);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static CompraRequest.Item item(String id, int cantidad) {
        return new CompraRequest.Item(id, cantidad);
    }

    private static CompraRequest.Entrega entrega(String tienda, String tipo) {
        return new CompraRequest.Entrega(tienda, tipo);
    }

    private static CompraRequest.Envio envio() {
        return new CompraRequest.Envio("Ana Pérez", "3001234567", "Cra 10 # 20-30", "Medellín", "Apto 301");
    }

    /** filtro×2 (s1, retiro) + pastilla×1 (s2, envío). */
    private static CompraRequest carritoMixto() {
        return new CompraRequest(List.of(item("filtro", 2), item("pastilla", 1)),
                List.of(entrega("s1", "RETIRO"), entrega("s2", "ENVIO")), envio(), null);
    }

    private static ResponseStatusException assertEstado(HttpStatus esperado, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(esperado, e.getStatusCode(), e.getReason());
        return e;
    }

    /** Líneas que se pagan a las tiendas (sin la comisión informativa de la plataforma). */
    private List<RepartoPago> repartosDeNegocio() {
        return repartos.stream().filter(r -> !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).toList();
    }

    private List<RepartoPago> comisiones() {
        return repartos.stream().filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).toList();
    }

    private PagoEntity iniciarYObtenerPago(CompraRequest r) {
        Map<String, Object> link = service.iniciarPago(CORREO, r);
        return pagos.values().stream().filter(p -> p.getReferencia().equals(link.get("referencia"))).findFirst().orElseThrow();
    }

    private PagoEntity pagado(PagoEntity p) {
        p.setEstado("PAGADO");
        p.setFechaPago(LocalDateTime.now());
        p.setTransactionId("txn-1");
        return p;
    }

    private List<MovimientoInventario> salidas(String productoId) {
        return movimientos.stream().filter(m -> "SALIDA".equals(m.getTipo()) && productoId.equals(m.getProductoId())).toList();
    }

    // ─── Cotizar: el backend calcula todo ────────────────────────────────────

    @Test
    void cotizaPorTiendaConIvaYEnvioFijoDeLaTienda() {
        CompraCotizadaDTO c = service.cotizar(CORREO, carritoMixto());

        assertEquals(2, c.entregas().size());
        CompraCatalogo.EntregaTienda s1 = c.entregas().get(0);
        assertEquals("s1", s1.getTiendaId());
        assertEquals("RETIRO", s1.getTipo());
        assertEquals(40_000.0, s1.getSubtotal());
        assertEquals(7_600.0, s1.getIva());
        assertEquals(0.0, s1.getCostoEnvio(), "retiro: sin costo");
        assertEquals(47_600.0, s1.getTotal());
        CompraCatalogo.EntregaTienda s2 = c.entregas().get(1);
        assertEquals("ENVIO", s2.getTipo());
        assertEquals(30_000.0, s2.getSubtotal());
        assertEquals(5_700.0, s2.getIva());
        assertEquals(8_000.0, s2.getCostoEnvio(), "el costo fijo lo define la tienda");
        assertEquals(43_700.0, s2.getTotal());
        assertEquals(70_000.0, c.subtotal());
        assertEquals(13_300.0, c.iva());
        assertEquals(8_000.0, c.costoEnvio());
        assertEquals(91_300.0, c.total());
        assertTrue(compras.isEmpty() && movimientos.isEmpty(), "cotizar no guarda ni descuenta nada");
        verifyNoInteractions(pagoService);
    }

    @Test
    void elPrecioSaleDelProductoNoDelNavegador() {
        // La petición solo trae productoId y cantidad: ni siquiera hay dónde mandar un precio
        for (RecordComponent c : CompraRequest.Item.class.getRecordComponents()) {
            assertTrue(c.getName().equals("productoId") || c.getName().equals("cantidad"), c.getName());
        }
        productos.get("filtro").setPrecioVenta(25_000.0);
        assertEquals(25_000.0, service.cotizar(CORREO, new CompraRequest(List.of(item("filtro", 1)),
                List.of(entrega("s1", "RETIRO")), null, null)).lineas().get(0).getPrecioUnitario());
    }

    @Test
    void elMismoProductoRepetidoSeSuma() {
        CompraCotizadaDTO c = service.cotizar(CORREO, new CompraRequest(List.of(item("filtro", 1), item("filtro", 2)),
                List.of(entrega("s1", "RETIRO")), null, null));
        assertEquals(1, c.lineas().size());
        assertEquals(3, c.lineas().get(0).getCantidad());
        assertEquals(60_000.0, c.lineas().get(0).getSubtotal());
    }

    @Test
    void validaElCarrito() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(List.of(), List.of(), null, null)));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 0)), List.of(entrega("s1", "RETIRO")), null, null)));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 100)), List.of(entrega("s1", "RETIRO")), null, null)));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item(" ", 1)), List.of(entrega("s1", "RETIRO")), null, null)));
    }

    @Test
    void cadaTiendaDelCarritoNecesitaSuFormaDeEntrega() {
        // Falta la entrega de s2
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 1), item("pastilla", 1)), List.of(entrega("s1", "RETIRO")), envio(), null)));
        // Entrega para una tienda que no está en el carrito
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 1)), List.of(entrega("s1", "RETIRO"), entrega("s2", "RETIRO")), null, null)));
        // Tipo inválido y duplicado
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 1)), List.of(entrega("s1", "DRON")), null, null)));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 1)), List.of(entrega("s1", "RETIRO"), entrega("s1", "RETIRO")), null, null)));
    }

    @Test
    void unaTiendaSinCostoDeEnvioSoloAdmiteRetiro() {
        ResponseStatusException e = assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 1)), List.of(entrega("s1", "ENVIO")), envio(), null)));
        assertTrue(e.getReason().contains("no hace envíos"));
    }

    @Test
    void elEnvioExigeLosDatosDeEntrega() {
        for (CompraRequest.Envio sinDatos : new CompraRequest.Envio[]{null,
                new CompraRequest.Envio("", "300", "Calle 1", "Medellín", null),
                new CompraRequest.Envio("Ana", " ", "Calle 1", "Medellín", null),
                new CompraRequest.Envio("Ana", "300", null, "Medellín", null),
                new CompraRequest.Envio("Ana", "300", "Calle 1", "", null)}) {
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                    List.of(item("pastilla", 1)), List.of(entrega("s2", "ENVIO")), sinDatos, null)));
        }
        // Solo retiro: la dirección no se pide
        assertDoesNotThrow(() -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "RETIRO")), null, null)));
    }

    @Test
    void sinStockSuficienteNoSeCotizaNiSeCobra() {
        ResponseStatusException e = assertEstado(HttpStatus.CONFLICT, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 4)), List.of(entrega("s2", "RETIRO")), null, null)));
        assertTrue(e.getReason().contains("solo hay 3"), e.getReason());
        productos.get("pastilla").setStockActual(0);
        e = assertEstado(HttpStatus.CONFLICT, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "RETIRO")), null, null)));
        assertTrue(e.getReason().contains("agotado"));
    }

    @Test
    void unEncargoNoNecesitaStock() {
        CompraCotizadaDTO c = service.cotizar(CORREO, new CompraRequest(List.of(item("disco", 3)),
                List.of(entrega("s2", "RETIRO")), null, null));
        assertEquals("ENCARGO", c.lineas().get(0).getTipo());
        assertEquals(4, c.lineas().get(0).getDiasEntrega());
    }

    @Test
    void losProductosDeUnaTiendaPorCompletarNoSePuedenComprar() {
        tiendas.get(1).setHorario(null); // s2 vuelve a quedar por completar
        assertEstado(HttpStatus.CONFLICT, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "RETIRO")), null, null)));
        // Un producto desactivado o inexistente tampoco
        productos.get("filtro").setActivo(false);
        assertEstado(HttpStatus.CONFLICT, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("filtro", 1)), List.of(entrega("s1", "RETIRO")), null, null)));
        assertEstado(HttpStatus.CONFLICT, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("fantasma", 1)), List.of(entrega("s1", "RETIRO")), null, null)));
    }

    // ─── Iniciar el pago ─────────────────────────────────────────────────────

    @Test
    void iniciarElPagoGuardaLaCompraYGeneraUnSoloLinkPorElTotal() {
        Map<String, Object> link = service.iniciarPago(CORREO, carritoMixto());

        assertEquals(1, compras.size());
        CompraCatalogo compra = compras.values().iterator().next();
        assertEquals(CompraCatalogo.PENDIENTE_PAGO, compra.getEstado());
        assertEquals(91_300.0, compra.getTotal());
        assertEquals(link.get("compraId"), compra.getId());
        assertEquals(link.get("referencia"), compra.getReferenciaPago());
        verify(pagoService, times(1)).crearPagoCatalogo("u1", 91_300.0, compra.getId());
        // El envío se guarda solo porque hay una tienda con ENVIO
        assertEquals("Cra 10 # 20-30", compra.getEnvioDireccion());
        // Nada se descuenta ni se reserva al generar el link
        assertEquals(5, productos.get("filtro").getStockActual());
        assertTrue(movimientos.isEmpty() && pedidos.isEmpty() && repartos.isEmpty());
    }

    @Test
    void antesDelLinkSeRevisaElStockDeTodasLasTiendas() {
        // s1 tiene stock, pero s2 no: no se genera link ni se guarda la compra
        productos.get("pastilla").setStockActual(0);
        assertEstado(HttpStatus.CONFLICT, () -> service.iniciarPago(CORREO, carritoMixto()));
        verify(pagoService, never()).crearPagoCatalogo(anyString(), anyDouble(), anyString());
        assertTrue(compras.isEmpty());
        // Y al revés: falta en la primera tienda
        productos.get("pastilla").setStockActual(3);
        productos.get("filtro").setStockActual(1);
        assertEstado(HttpStatus.CONFLICT, () -> service.iniciarPago(CORREO, carritoMixto()));
        verify(pagoService, never()).crearPagoCatalogo(anyString(), anyDouble(), anyString());
    }

    @Test
    void siFallaElLinkNoQuedaUnaCompraHuerfana() {
        when(pagoService.crearPagoCatalogo(anyString(), anyDouble(), anyString())).thenThrow(new RuntimeException("Wompi caído"));
        assertThrows(RuntimeException.class, () -> service.iniciarPago(CORREO, carritoMixto()));
        assertTrue(compras.isEmpty());
    }

    // ─── Aplicar el pago: stock, un pedido por tienda y reparto ──────────────

    @Test
    void elPagoDescuentaElStockCreaUnPedidoPorTiendaYRepartePorTienda() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));

        service.aplicar(pago);

        // Stock: bajó una vez, con su SALIDA y la referencia del pago
        assertEquals(3, productos.get("filtro").getStockActual());
        assertEquals(2, productos.get("pastilla").getStockActual());
        assertEquals(1, salidas("filtro").size());
        assertEquals(1, salidas("pastilla").size());
        assertEquals("PAGO-" + pago.getReferencia(), salidas("filtro").get(0).getReferencia());
        assertEquals(2, salidas("filtro").get(0).getCantidad());

        // Un pedido por tienda, de origen CATALOGO y sin taller ni orden
        assertEquals(2, pedidos.size());
        PedidoTienda p1 = pedidos.values().stream().filter(p -> p.getTiendaId().equals("s1")).findFirst().orElseThrow();
        PedidoTienda p2 = pedidos.values().stream().filter(p -> p.getTiendaId().equals("s2")).findFirst().orElseThrow();
        for (PedidoTienda p : List.of(p1, p2)) {
            assertTrue(p.esCatalogo());
            assertNull(p.getOrdenId());
            assertNull(p.getTallerId());
            assertEquals(PedidoTienda.PENDIENTE, p.getEstado());
            assertEquals("u1", p.getClienteId());
            assertNotNull(p.getPlazoDespachoHasta());
        }
        assertEquals(PedidoTienda.RETIRO, p1.getEntrega());
        assertEquals(47_600.0, p1.getTotal());
        assertEquals("Calle s1", p1.getTiendaDireccion());
        assertEquals("L-S 8am-6pm", p1.getTiendaHorario());
        assertNull(p1.getEnvioDireccion(), "en retiro no se guarda la dirección del cliente");
        assertEquals(PedidoTienda.ENVIO, p2.getEntrega());
        assertEquals(43_700.0, p2.getTotal());
        assertEquals(8_000.0, p2.getCostoEnvio());
        assertEquals("Cra 10 # 20-30", p2.getEnvioDireccion());
        assertEquals("3001234567", p2.getEnvioTelefono());
        assertNull(p2.getCodigoRetiroHash(), "un envío no tiene código de retiro");

        // El pago se reparte a la tienda dueña de cada producto: lo pagado = lo repartido
        assertEquals(2, repartosDeNegocio().size());
        for (RepartoPago r : repartosDeNegocio()) {
            assertEquals(RepartoPago.TIENDA, r.getBeneficiarioTipo());
            assertEquals("CATALOGO", r.getConcepto());
            assertEquals(RepartoPago.PENDIENTE, r.getEstado());
            assertTrue(Boolean.TRUE.equals(r.getLiquidacionPorEvento()));
            assertNotNull(r.getCompraCatalogoId());
            assertNull(r.getOrdenServicioId());
        }
        assertEquals(47_600.0, repartosDeNegocio().stream().filter(r -> r.getBeneficiarioId().equals("s1")).findFirst().orElseThrow().getMonto());
        assertEquals(43_700.0, repartosDeNegocio().stream().filter(r -> r.getBeneficiarioId().equals("s2")).findFirst().orElseThrow().getMonto());
        assertEquals(91_300.0, repartosDeNegocio().stream().mapToDouble(RepartoPago::getMonto).sum());
        assertEquals(p1.getId(), repartosDeNegocio().stream().filter(r -> r.getBeneficiarioId().equals("s1")).findFirst().orElseThrow().getPedidoTiendaId());

        // Comisión de la plataforma: 10% del subtotal antes de IVA (70.000, sin envío), solo informativa
        assertEquals(1, comisiones().size());
        RepartoPago comision = comisiones().get(0);
        assertEquals(7_000.0, comision.getMonto());
        assertEquals(RepartoPago.INFORMATIVA, comision.getEstado());
        assertEquals("CATALOGO", comision.getConcepto());
        assertNotNull(comision.getCompraCatalogoId());
        assertNull(comision.getPedidoTiendaId(), "sin pedido: ninguna liquidación ni anulación la toca");
        assertFalse(Boolean.TRUE.equals(comision.getLiquidacionPorEvento()));

        CompraCatalogo compra = compras.values().iterator().next();
        assertEquals(CompraCatalogo.PAGADA, compra.getEstado());
        assertEquals(2, compra.getPedidoIds().size());
        assertTrue(reembolsos.isEmpty());
    }

    @Test
    void elCodigoDeRetiroSoloSeGuardaComoHashYSoloLlegaAlCliente() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        service.aplicar(pago);

        ArgumentCaptor<Map<String, String>> codigos = ArgumentCaptor.forClass(Map.class);
        verify(avisos).compraPagada(any(CompraCatalogo.class), anyList(), codigos.capture(), eq(pago));
        PedidoTienda retiro = pedidos.values().stream().filter(p -> PedidoTienda.RETIRO.equals(p.getEntrega())).findFirst().orElseThrow();
        PedidoTienda envio = pedidos.values().stream().filter(p -> PedidoTienda.ENVIO.equals(p.getEntrega())).findFirst().orElseThrow();

        // El código en claro va solo en el comprobante del cliente, y solo para el pedido con retiro
        assertEquals(1, codigos.getValue().size());
        String codigo = codigos.getValue().get(retiro.getId());
        assertNotNull(codigo);
        assertFalse(codigos.getValue().containsKey(envio.getId()));
        // En la base solo hay su hash, ligado a ese pedido
        assertNotNull(retiro.getCodigoRetiroHash());
        assertFalse(retiro.getCodigoRetiroHash().contains(codigo.replace("-", "")));
        assertTrue(CodigoRetiro.coincide(retiro.getCodigoRetiroHash(), retiro.getId(), codigo));
        assertFalse(Boolean.TRUE.equals(retiro.getCodigoRetiroUsado()));
        // El código no se serializa hacia la tienda (campos con @JsonIgnore)
        assertTrue(ignoradoPorJson("codigoRetiroHash") && ignoradoPorJson("codigoRetiroIntentos") && ignoradoPorJson("codigoRetiroBloqueadoHasta"));
    }

    private static boolean ignoradoPorJson(String campo) {
        try {
            return PedidoTienda.class.getDeclaredField(campo).isAnnotationPresent(com.fasterxml.jackson.annotation.JsonIgnore.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Test
    void unEncargoNoDescuentaStock() {
        PagoEntity pago = pagado(iniciarYObtenerPago(new CompraRequest(List.of(item("disco", 2), item("filtro", 1)),
                List.of(entrega("s1", "RETIRO"), entrega("s2", "RETIRO")), null, null)));
        service.aplicar(pago);
        assertEquals(0, productos.get("disco").getStockActual());
        assertTrue(salidas("disco").isEmpty());
        assertEquals(4, productos.get("filtro").getStockActual());
        PedidoTienda p = pedidos.values().stream().filter(x -> x.getTiendaId().equals("s2")).findFirst().orElseThrow();
        assertEquals(4, p.getDiasEntrega());
        // Plazo: días estimados + 2 días hábiles
        assertTrue(p.getPlazoDespachoHasta().isAfter(p.getFechaCreacion().plusDays(5)));
    }

    @Test
    void aplicarDosVecesNoRepiteStockPedidosNiReparto() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        service.aplicar(pago);
        service.aplicar(pago);
        service.aplicar(pago);

        assertEquals(3, productos.get("filtro").getStockActual());
        assertEquals(2, productos.get("pastilla").getStockActual());
        assertEquals(2, movimientos.size());
        assertEquals(2, pedidos.size());
        assertEquals(2, repartosDeNegocio().size());
        assertEquals(1, comisiones().size(), "reprocesar un pago ya aplicado no repite la comisión");
        assertTrue(reembolsos.isEmpty(), "reprocesar un pago ya aplicado no genera reembolsos");
        verify(avisos, times(1)).compraPagada(any(), anyList(), anyMap(), any());
    }

    // ─── Todo o nada ─────────────────────────────────────────────────────────

    @Test
    void siUnaTiendaSeQuedaSinStockDespuesDePagarSeRevierteTodoYSeReembolsaTodo() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        // Entre el link y el pago alguien más compró la pastilla de s2
        productos.get("pastilla").setStockActual(0);

        service.aplicar(pago);

        // s1 ya se había descontado: se repone con una ENTRADA registrada
        assertEquals(5, productos.get("filtro").getStockActual());
        assertEquals(1, salidas("filtro").size());
        assertEquals(1, movimientos.stream().filter(m -> "ENTRADA".equals(m.getTipo()) && "filtro".equals(m.getProductoId())).count());
        assertTrue(salidas("pastilla").isEmpty());
        // Nada se creó: ni pedidos ni reparto
        assertTrue(pedidos.isEmpty());
        assertTrue(repartos.isEmpty());
        // La compra queda NO_APLICADA con un reembolso por el pago completo
        CompraCatalogo compra = compras.values().iterator().next();
        assertEquals(CompraCatalogo.NO_APLICADA, compra.getEstado());
        assertTrue(compra.getMotivoNoAplicada().contains("Producto pastilla"));
        assertEquals(1, reembolsos.size());
        Reembolso r = reembolsos.get(0);
        assertEquals(91_300.0, r.getMonto());
        assertEquals(Reembolso.PENDIENTE, r.getEstado());
        assertEquals(compra.getId(), r.getCompraCatalogoId());
        assertEquals(pago.getReferencia(), r.getReferenciaPago());
        verify(avisos).compraNoAplicada(eq(compra), eq(91_300.0), contains("Producto pastilla"));
        verify(avisos, never()).compraPagada(any(), anyList(), anyMap(), any());
    }

    @Test
    void siSeAgotaLaPrimeraTiendaTampocoSeAplicaNada() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        productos.get("filtro").setStockActual(1);
        service.aplicar(pago);
        assertEquals(1, productos.get("filtro").getStockActual());
        assertEquals(3, productos.get("pastilla").getStockActual());
        assertTrue(pedidos.isEmpty() && repartos.isEmpty());
        assertEquals(1, reembolsos.size());
        assertEquals(CompraCatalogo.NO_APLICADA, compras.values().iterator().next().getEstado());
    }

    @Test
    void siUnProductoSeDesactivaEntreElLinkYElPagoTambienEsTodoONada() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        productos.get("pastilla").setActivo(false);
        service.aplicar(pago);
        assertEquals(5, productos.get("filtro").getStockActual());
        assertTrue(pedidos.isEmpty());
        assertEquals(1, reembolsos.size());
    }

    @Test
    void siUnaTiendaPierdeSusDatosEntreElLinkYElPagoNoSeAplica() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        tiendas.get(0).setHorario(null);
        service.aplicar(pago);
        assertTrue(pedidos.isEmpty());
        assertEquals(1, reembolsos.size());
        assertEquals(5, productos.get("filtro").getStockActual());
    }

    @Test
    void siElMontoPagadoNoCoincideNoSeAplicaYSeReembolsa() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        pago.setMonto(50_000.0);
        service.aplicar(pago);
        assertTrue(pedidos.isEmpty() && movimientos.isEmpty());
        assertEquals(1, reembolsos.size());
        assertEquals(50_000.0, reembolsos.get(0).getMonto());
    }

    @Test
    void unPagoSinCompraSeReembolsa() {
        PagoEntity pago = new PagoEntity();
        pago.setId(7L);
        pago.setReferencia("DRV-X");
        pago.setMonto(10_000.0);
        pago.setCompraCatalogoId("no-existe");
        service.aplicar(pago);
        assertEquals(1, reembolsos.size());
        verify(avisos).alertaAdmin(anyString(), contains("DRV-X"));
    }

    @Test
    void unPagoParaUnaCompraQueYaNoEsperabaPagoSeReembolsa() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        compras.values().iterator().next().setEstado(CompraCatalogo.NO_APLICADA);
        service.aplicar(pago);
        assertEquals(1, reembolsos.size());
        assertTrue(pedidos.isEmpty());
    }

    @Test
    void siFallaAlCrearLosPedidosSeDeshaceTodoYSeReembolsa() {
        PagoEntity pago = pagado(iniciarYObtenerPago(carritoMixto()));
        // El primer pedido se guarda, el segundo falla
        when(pedidoRepository.save(any(PedidoTienda.class))).thenAnswer(i -> {
            PedidoTienda p = i.getArgument(0);
            if (!pedidos.isEmpty()) throw new RuntimeException("Mongo caído");
            pedidos.put(p.getId(), p);
            return p;
        });

        assertDoesNotThrow(() -> service.aplicar(pago));

        // Se deshace todo: stock repuesto, sin pedidos ni repartos, compra NO_APLICADA y reembolso completo
        assertEquals(5, productos.get("filtro").getStockActual());
        assertEquals(3, productos.get("pastilla").getStockActual());
        assertTrue(pedidos.isEmpty(), "el pedido ya creado se borra");
        assertTrue(repartos.isEmpty(), "y su reparto también");
        assertEquals(CompraCatalogo.NO_APLICADA, compras.values().iterator().next().getEstado());
        assertEquals(1, reembolsos.size());
        assertEquals(91_300.0, reembolsos.get(0).getMonto());
        verify(avisos, never()).compraPagada(any(), anyList(), anyMap(), any());
    }

    // ─── Integración con la confirmación real del pago ───────────────────────

    @Test
    void rechazadoYLuegoAprobadoConLaMismaReferenciaSeAplicaUnaSolaVez() {
        PagoEntity fila = iniciarYObtenerPago(carritoMixto());
        PagoRepository repoPagos = mock(PagoRepository.class);
        when(repoPagos.reclamar(anyLong(), anyString())).thenAnswer(i -> {
            if (!"PENDIENTE".equals(fila.getEstado()) && !"RECHAZADO".equals(fila.getEstado())) return 0;
            fila.setEstado("PROCESANDO");
            return 1;
        });
        when(repoPagos.save(any())).thenAnswer(i -> i.getArgument(0));
        PagoMarketplaceService pagoMarketplace = new PagoMarketplaceService(pagoService, repoPagos,
                mock(OrdenServicioRepository.class), mock(CotizacionRepository.class), mock(PedidoTiendaRepository.class),
                mock(RepartoPagoRepository.class), mock(ReembolsoRepository.class), mock(TallerRepository.class),
                mock(ProductoService.class), mock(MovimientoInventarioService.class), mock(MovimientoInventarioRepository.class),
                mock(UsuarioService.class), mock(TransicionOrdenService.class), mock(CotizacionService.class), avisos,
                mock(ParametrosMarketplace.class));
        pagoMarketplace.setCatalogo(service);
        fila.setEstado("RECHAZADO"); // Wompi declinó el primer intento

        pagoMarketplace.confirmar(fila, "txn-aprobada");
        pagoMarketplace.confirmar(fila, "txn-aprobada"); // webhook + redirección + "Ya pagué"
        pagoMarketplace.confirmar(fila, "txn-aprobada");

        assertEquals(3, productos.get("filtro").getStockActual());
        assertEquals(2, movimientos.size());
        assertEquals(2, pedidos.size());
        assertEquals(2, repartosDeNegocio().size());
        assertEquals(1, comisiones().size());
        assertTrue(reembolsos.isEmpty());
        assertEquals(CompraCatalogo.PAGADA, compras.values().iterator().next().getEstado());
        assertEquals("CATALOGO", fila.getConcepto());
        assertTrue(PagoMarketplaceService.esMarketplace("CATALOGO"));
    }

    // ─── Mis compras y códigos ───────────────────────────────────────────────

    @Test
    void misComprasMuestraLosPedidosSinElCodigo() {
        service.aplicar(pagado(iniciarYObtenerPago(carritoMixto())));
        List<CompraDTO> mias = service.misCompras(CORREO);
        assertEquals(1, mias.size());
        assertEquals(2, mias.get(0).pedidos().size());
        assertEquals(91_300.0, mias.get(0).total());
        for (RecordComponent c : CompraDTO.PedidoCompraDTO.class.getRecordComponents()) {
            assertFalse(c.getName().toLowerCase().contains("hash") || c.getName().equals("codigo"), "no debe exponer " + c.getName());
        }
        assertEquals("L-S 8am-6pm", mias.get(0).pedidos().stream().filter(p -> p.tiendaId().equals("s1")).findFirst().orElseThrow().tiendaHorario());
    }

    private PedidoTienda pedidoDeRetiro() {
        service.aplicar(pagado(iniciarYObtenerPago(carritoMixto())));
        return pedidos.values().stream().filter(p -> PedidoTienda.RETIRO.equals(p.getEntrega())).findFirst().orElseThrow();
    }

    @Test
    void elClientePuedeGenerarUnCodigoNuevoQueReemplazaAlAnterior() {
        PedidoTienda retiro = pedidoDeRetiro();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(retiro);

        Map<String, Object> r = service.nuevoCodigoRetiro(CORREO, retiro.getId());

        // El código llega por correo, nunca en la respuesta (y por tanto nunca en pantalla)
        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(avisos).codigoRetiroNuevo(eq(retiro), enviado.capture());
        String codigo = enviado.getValue();
        assertTrue(CodigoRetiro.normalizar(codigo) != null);
        assertFalse(r.containsKey("codigo"));
        assertFalse(r.toString().contains(codigo.replace("-", "")) || r.toString().contains(codigo), r.toString());
        ArgumentCaptor<Update> cambios = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(consulta.capture(), cambios.capture(), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
        // Solo se guarda el hash del nuevo, ligado al pedido; el código en claro no se persiste
        String guardado = cambios.getValue().getUpdateObject().toString();
        assertTrue(guardado.contains(CodigoRetiro.hash(retiro.getId(), codigo)), guardado);
        assertFalse(guardado.contains(codigo), guardado);
        // La escritura es condicional: pedido del cliente, aún sin retirar y con código sin usar
        String filtro = consulta.getValue().getQueryObject().toString();
        assertTrue(filtro.contains("clienteId") && filtro.contains("codigoRetiroUsado"), filtro);
    }

    @Test
    void al_emitir_y_renovar_un_codigo_ni_el_codigo_ni_su_hash_van_al_log() {
        java.util.List<String> logs = com.drivemaster.drivemaster.testutil.LogsCapturados.de(() -> {
            PedidoTienda retiro = pedidoDeRetiro();
            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                    .thenReturn(retiro);
            service.nuevoCodigoRetiro(CORREO, retiro.getId());
        });

        ArgumentCaptor<Map<String, String>> codigos = ArgumentCaptor.forClass(Map.class);
        verify(avisos).compraPagada(any(CompraCatalogo.class), anyList(), codigos.capture(), any());
        PedidoTienda retiro = pedidos.values().stream().filter(p -> PedidoTienda.RETIRO.equals(p.getEntrega())).findFirst().orElseThrow();
        String emitido = codigos.getValue().get(retiro.getId());

        String todo = String.join("\n", logs);
        assertFalse(todo.contains(retiro.getCodigoRetiroHash()), "el hash no debe aparecer en el log");
        assertFalse(todo.contains(emitido) || todo.contains(emitido.replace("-", "")), "el código no debe aparecer en el log");
    }

    @Test
    void unCodigoYaUsadoOUnPedidoRetiradoNoAdmiteCodigoNuevo() {
        PedidoTienda retiro = pedidoDeRetiro();
        // La escritura condicional no encuentra el pedido (ya retirado)
        assertEstado(HttpStatus.CONFLICT, () -> service.nuevoCodigoRetiro(CORREO, retiro.getId()));
        verify(avisos, never()).codigoRetiroNuevo(any(), anyString());
    }

    @Test
    void soloElDuenoDeLaCompraPideElCodigo() {
        PedidoTienda retiro = pedidoDeRetiro();
        retiro.setClienteId("otro-cliente");
        assertEstado(HttpStatus.NOT_FOUND, () -> service.nuevoCodigoRetiro(CORREO, retiro.getId()));
        assertEstado(HttpStatus.NOT_FOUND, () -> service.nuevoCodigoRetiro(CORREO, "no-existe"));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
    }

    @Test
    void unEnvioNoTieneCodigoDeRetiro() {
        service.aplicar(pagado(iniciarYObtenerPago(carritoMixto())));
        PedidoTienda envio = pedidos.values().stream().filter(p -> PedidoTienda.ENVIO.equals(p.getEntrega())).findFirst().orElseThrow();
        assertEstado(HttpStatus.CONFLICT, () -> service.nuevoCodigoRetiro(CORREO, envio.getId()));
    }

    // ─── Envío solo a la misma ciudad de la tienda ───────────────────────────

    @Test
    void laTiendaSoloEnviaDentroDeSuCiudad() {
        CompraRequest.Envio otraCiudad = new CompraRequest.Envio("Ana", "3001234567", "Cra 1 # 2-3", "Bogotá", null);
        ResponseStatusException e = assertEstado(HttpStatus.BAD_REQUEST, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "ENVIO")), otraCiudad, null)));
        assertTrue(e.getReason().contains("solo envía dentro de Medellín"), e.getReason());
        assertTrue(e.getReason().contains("retiro"));
        // Y no se genera link ni se guarda la compra
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.iniciarPago(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "ENVIO")), otraCiudad, null)));
        verify(pagoService, never()).crearPagoCatalogo(anyString(), anyDouble(), anyString());
        assertTrue(compras.isEmpty());
    }

    @Test
    void laCiudadSeComparaSinTildesNiMayusculas() {
        CompraRequest.Envio mismaCiudad = new CompraRequest.Envio("Ana", "3001234567", "Cra 1 # 2-3", "  MEDELLIN ", null);
        CompraCotizadaDTO c = service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "ENVIO")), mismaCiudad, null));
        assertEquals(8_000.0, c.costoEnvio());
    }

    @Test
    void elRetiroNoDependeDeLaCiudadDelCliente() {
        // Retirar en la tienda no exige que el cliente viva en la misma ciudad
        assertDoesNotThrow(() -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "RETIRO")), null, null)));
    }

    @Test
    void unCarritoMixtoValidaLaCiudadSoloDeLasTiendasConEnvio() {
        // s1 (Bogotá) por retiro y s2 (Medellín) por envío a Medellín: válido aunque el cliente no viva en Bogotá
        assertDoesNotThrow(() -> service.cotizar(CORREO, carritoMixto()));
    }

    // ─── DriveMaster no es una tienda: un solo carrito para todas ────────────

    @Test
    void todasLasTiendasSePaganConElMismoCarritoSinReglasEspeciales() {
        assertDoesNotThrow(() -> service.iniciarPago(CORREO, carritoMixto()));
        assertEquals(1, compras.size());
    }

    @Test
    void unProductoDeUnaTiendaQueDejoDeSerVisibleYaNoSePuedeComprar() {
        // La tienda s2 pierde su horario: deja de ser visible y sus productos salen del catálogo
        tiendas.stream().filter(t -> t.getId().equals("s2")).findFirst().orElseThrow().setHorario(null);

        ResponseStatusException e = assertEstado(HttpStatus.CONFLICT, () -> service.cotizar(CORREO, new CompraRequest(
                List.of(item("pastilla", 1)), List.of(entrega("s2", "RETIRO")), null, null)));

        assertTrue(e.getReason().contains("ya no está disponible"), e.getReason());
        assertTrue(compras.isEmpty());
    }
}
