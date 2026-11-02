package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.catalogo.CompraRequest;
import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CompraCatalogoRepository;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ResolucionRepartoRepository;
import com.drivemaster.drivemaster.service.dispersion.DispersionService;
import com.drivemaster.drivemaster.util.CalculadoraCotizacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Escenarios de aceptación del stock de talleres, de punta a punta: compra del taller (CompraCatalogoService real),
 * llegada (InventarioTallerService real), cobro (PagoMarketplaceService real) y cancelación (CancelacionOrdenService
 * real). Solo la persistencia está en memoria, con el inventario de la tienda y el del taller reales.
 */
class StockTallerEscenariosTest {

    private static final String CORREO_TALLER = "taller@demo.com";
    private static final String CORREO_ADMIN = "admin@demo.com";
    private static final String PIEZA = PiezaTaller.idDe("t1", "REF:BP1");
    private static final String MOTIVO = "El cliente desistió antes de instalar";

    private final InventarioEnMemoria inventario = new InventarioEnMemoria();
    private final Map<String, Producto> productos = new HashMap<>();
    private final List<MovimientoInventario> movimientosTienda = new ArrayList<>();
    private final Map<String, CompraCatalogo> compras = new LinkedHashMap<>();
    private final Map<String, PedidoTienda> pedidos = new LinkedHashMap<>();
    private final Map<Long, PagoEntity> pagos = new HashMap<>();
    private final List<RepartoPago> lineas = new ArrayList<>();
    private final List<Reembolso> reembolsos = new ArrayList<>();
    private final Map<String, Cotizacion> cotizaciones = new HashMap<>();
    private final Map<String, OrdenServicio> ordenes = new HashMap<>();
    private long secuencia = 100;

    private final Taller taller = Taller.builder().id("t1").nombre("AutoMax").estadoAprobacion("APROBADO").activo(true)
            .responsableIva(true).build();
    private final Tienda tienda = Tienda.builder().id("s1").nombre("Repuestos Sur").ciudad("Bogotá").direccion("Cra 1")
            .costoEnvio(5_000.0).build();

    private PagoRepository pagoRepository;
    private RepartoPagoRepository repartoRepository;
    private ParametrosMarketplace parametros;
    private CompraCatalogoService compra;
    private PagoMarketplaceService pagoMarketplace;
    private InventarioTallerService inventarioTaller;
    private CancelacionOrdenService cancelacion;
    private AvisosSolicitudServicio avisos;
    private CotizacionService cotizacionService;

    @BeforeEach
    void setUp() {
        Producto p = new Producto();
        p.setId("p1");
        p.setNombre("Pastillas");
        p.setReferencia("BP1");
        p.setTipo("STOCK");
        p.setTiendaId("s1");
        p.setStockActual(20);
        p.setPrecioVenta(100_000.0);
        p.setCompatibilidadUniversal(true);
        productos.put("p1", p);

        parametros = mock(ParametrosMarketplace.class);
        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.comisionRetenida()).thenReturn(true);
        when(parametros.plazoDespachoDiasHabiles()).thenReturn(2);
        when(parametros.plazoEncargoDiasExtra()).thenReturn(2);

        ProductoService productoService = mock(ProductoService.class);
        when(productoService.obtenerPorId(anyString())).thenAnswer(i -> productos.get(i.<String>getArgument(0)));
        when(productoService.descontarStock(anyString(), anyInt())).thenAnswer(i -> {
            Producto x = productos.get(i.<String>getArgument(0));
            int cantidad = i.getArgument(1);
            if (x.getStockActual() < cantidad) throw new StockInsuficienteException(x.getNombre(), x.getStockActual());
            x.setStockActual(x.getStockActual() - cantidad);
            return x;
        });
        when(productoService.incrementarStock(anyString(), anyInt())).thenAnswer(i -> {
            Producto x = productos.get(i.<String>getArgument(0));
            x.setStockActual(x.getStockActual() + i.<Integer>getArgument(1));
            return x;
        });
        MovimientoInventarioRepository movimientoRepository = mock(MovimientoInventarioRepository.class);
        when(movimientoRepository.save(any())).thenAnswer(i -> {
            movimientosTienda.add(i.getArgument(0));
            return i.getArgument(0);
        });
        MovimientoInventarioService movimientoService = new MovimientoInventarioServiceImpl(movimientoRepository, productoService);

        PedidoTiendaRepository pedidoRepository = mock(PedidoTiendaRepository.class);
        when(pedidoRepository.save(any())).thenAnswer(i -> {
            PedidoTienda x = i.getArgument(0);
            if (x.getId() == null) x.setId("ped-" + (++secuencia));
            pedidos.put(x.getId(), x);
            return x;
        });
        when(pedidoRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(pedidos.get(i.<String>getArgument(0))));
        when(pedidoRepository.findByPagoId(anyLong())).thenAnswer(i -> pedidos.values().stream()
                .filter(x -> i.<Long>getArgument(0).equals(x.getPagoId())).toList());
        when(pedidoRepository.findByCompradorTallerIdAndFechaEntradaInventarioIsNullOrderByFechaCreacionDesc(anyString()))
                .thenAnswer(i -> pedidos.values().stream().filter(x -> i.<String>getArgument(0).equals(x.getCompradorTallerId())
                        && x.getFechaEntradaInventario() == null).toList());

        CompraCatalogoRepository compraRepository = mock(CompraCatalogoRepository.class);
        when(compraRepository.save(any())).thenAnswer(i -> {
            CompraCatalogo c = i.getArgument(0);
            if (c.getId() == null) c.setId("com-" + (++secuencia));
            compras.put(c.getId(), c);
            return c;
        });
        when(compraRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(compras.get(i.<String>getArgument(0))));

        repartoRepository = mock(RepartoPagoRepository.class);
        when(repartoRepository.save(any())).thenAnswer(i -> {
            RepartoPago r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(++secuencia);
                lineas.add(r);
            }
            return r;
        });
        when(repartoRepository.findByPagoId(anyLong())).thenAnswer(i -> lineas.stream().filter(r -> i.<Long>getArgument(0).equals(r.getPagoId())).toList());
        when(repartoRepository.findByPedidoTiendaId(anyString())).thenAnswer(i -> lineas.stream()
                .filter(r -> i.<String>getArgument(0).equals(r.getPedidoTiendaId())).toList());
        when(repartoRepository.findByOrdenServicioIdAndBeneficiarioTipo(anyString(), anyString())).thenAnswer(i -> lineas.stream()
                .filter(r -> i.<String>getArgument(0).equals(r.getOrdenServicioId()) && i.<String>getArgument(1).equals(r.getBeneficiarioTipo())).toList());
        when(repartoRepository.anularPendiente(anyLong(), anyString())).thenAnswer(i -> {
            RepartoPago r = lineas.stream().filter(x -> i.<Long>getArgument(0).equals(x.getId())).findFirst().orElseThrow();
            if (!RepartoPago.PENDIENTE.equals(r.getEstado())) return 0;
            r.setEstado(RepartoPago.ANULADO);
            return 1;
        });
        when(repartoRepository.sumaComisionDePago(anyLong())).thenAnswer(i -> lineas.stream()
                .filter(r -> i.<Long>getArgument(0).equals(r.getPagoId()) && RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()))
                .mapToDouble(RepartoPago::getMonto).sum());

        ReembolsoRepository reembolsoRepository = mock(ReembolsoRepository.class);
        when(reembolsoRepository.save(any())).thenAnswer(i -> {
            Reembolso r = i.getArgument(0);
            r.setId(++secuencia);
            reembolsos.add(r);
            return r;
        });
        when(reembolsoRepository.findByPagoId(anyLong())).thenAnswer(i -> reembolsos.stream()
                .filter(r -> i.<Long>getArgument(0).equals(r.getPagoId())).toList());

        pagoRepository = mock(PagoRepository.class);
        when(pagoRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(pagos.get(i.<Long>getArgument(0))));
        when(pagoRepository.findByReferencia(anyString())).thenAnswer(i -> pagos.values().stream()
                .filter(x -> i.<String>getArgument(0).equals(x.getReferencia())).findFirst());
        when(pagoRepository.reclamar(anyLong(), anyString())).thenReturn(1);

        OrdenServicioRepository ordenRepository = mock(OrdenServicioRepository.class);
        when(ordenRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(ordenes.get(i.<String>getArgument(0))));
        CotizacionRepository cotizacionRepository = mock(CotizacionRepository.class);
        when(cotizacionRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(cotizaciones.get(i.<String>getArgument(0))));
        when(cotizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenAnswer(i -> {
                    PedidoTienda x = pedidos.get(((Query) i.getArgument(0)).getQueryObject().getString("_id"));
                    if (x == null) x = pedidos.values().stream().filter(y -> i.getArgument(0).toString().contains(y.getId())).findFirst().orElseThrow();
                    if (!List.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION, PedidoTienda.DESPACHADO).contains(x.getEstado())) return null;
                    x.setEstado(String.valueOf(((Update) i.getArgument(1)).getUpdateObject().get("$set", org.bson.Document.class).get("estado")));
                    return x;
                });

        UsuarioService usuarioService = mock(UsuarioService.class);
        when(usuarioService.obtenerPorCorreo(CORREO_TALLER)).thenReturn(Usuario.builder().id("u-taller").nombre("AutoMax").correo(CORREO_TALLER).rol("TALLER").build());
        when(usuarioService.obtenerPorCorreo(CORREO_ADMIN)).thenReturn(Usuario.builder().id("admin").nombre("Ada").correo(CORREO_ADMIN).rol("SUPERADMIN").build());
        TallerService tallerService = mock(TallerService.class);
        when(tallerService.obtenerPorUsuario(CORREO_TALLER)).thenReturn(taller);
        TallerRepository tallerRepository = mock(TallerRepository.class);
        when(tallerRepository.findById("t1")).thenReturn(Optional.of(taller));

        avisos = mock(AvisosSolicitudServicio.class);
        TransicionOrdenService transiciones = mock(TransicionOrdenService.class);
        when(transiciones.transicionar(any(), anyString(), any(), anyString(), any(), any())).thenAnswer(i -> {
            OrdenServicio o = i.getArgument(0);
            String destino = i.getArgument(1);
            if (!EstadoOrden.CANCELADA.equals(destino) || CancelacionOrdenService.CANCELABLES.contains(o.getEstado())) o.setEstado(destino);
            else throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden cambió");
            Update cambios = i.getArgument(5);
            if (cambios != null && cambios.getUpdateObject().get("$set", org.bson.Document.class).containsKey("pagoCotizacionId")) {
                o.setPagoCotizacionId(cambios.getUpdateObject().get("$set", org.bson.Document.class).getLong("pagoCotizacionId"));
            }
            return o;
        });
        cotizacionService = mock(CotizacionService.class);

        PagoService pagoService = mock(PagoService.class);
        when(pagoService.crearPagoCatalogo(anyString(), anyDouble(), anyString())).thenAnswer(i -> {
            PagoEntity pago = new PagoEntity();
            pago.setId(++secuencia);
            pago.setReferencia("REF-" + pago.getId());
            pago.setConcepto(PagoMarketplaceService.CATALOGO);
            pago.setMonto(i.getArgument(1));
            pago.setUsuarioId(i.getArgument(0));
            pago.setCompraCatalogoId(i.getArgument(2));
            pago.setEstado("PENDIENTE");
            pagos.put(pago.getId(), pago);
            return Map.of("referencia", pago.getReferencia());
        });

        CatalogoService catalogo = mock(CatalogoService.class);
        when(catalogo.tiendasVisibles()).thenReturn(Map.of("s1", tienda));

        compra = new CompraCatalogoService(catalogo, mongo, compraRepository, pedidoRepository, repartoRepository, reembolsoRepository,
                pagoRepository, pagoService, usuarioService, mock(VehiculoService.class), productoService, movimientoService,
                movimientoRepository, parametros, avisos);
        compra.setTallerService(tallerService);

        pagoMarketplace = new PagoMarketplaceService(pagoService, pagoRepository, ordenRepository, cotizacionRepository, pedidoRepository,
                repartoRepository, reembolsoRepository, tallerRepository, productoService, movimientoService, movimientoRepository,
                usuarioService, transiciones, cotizacionService, avisos, parametros);
        pagoMarketplace.setMongoTemplate(mongo);
        pagoMarketplace.setCatalogo(compra);
        pagoMarketplace.setStockTaller(inventario.stock);

        RecepcionEnvioService recepcion = mock(RecepcionEnvioService.class);
        when(recepcion.confirmarRecepcion(anyString(), anyString())).thenAnswer(i -> {
            PedidoTienda x = pedidos.get(i.<String>getArgument(1));
            x.setEstado(PedidoTienda.ENTREGADO);
            return x;
        });
        inventarioTaller = new InventarioTallerService(tallerService, inventario.piezaRepository, pedidoRepository, productoService,
                inventario.stock, recepcion, mongo, parametros);

        LiquidacionService liquidacion = new LiquidacionService(repartoRepository, reembolsoRepository, pagoRepository, mock(DispersionService.class),
                avisos, mock(ResolucionRepartoRepository.class));
        cancelacion = new CancelacionOrdenService(ordenRepository, cotizacionRepository, pedidoRepository, pagoRepository, repartoRepository,
                reembolsoRepository, transiciones, pagoMarketplace, inventario.stock, movimientoService, liquidacion, usuarioService, avisos, mongo);
    }

    // ─── Pasos ───────────────────────────────────────────────────────────────

    /** El taller compra en el catálogo, paga, la tienda despacha y el taller confirma que llegó. */
    private void elTallerCompraYRecibe(int cantidad) {
        CompraRequest carrito = new CompraRequest(List.of(new CompraRequest.Item("p1", cantidad)),
                List.of(new CompraRequest.Entrega("s1", PedidoTienda.RETIRO)), null, null);
        Map<String, Object> link = compra.iniciarPago(CORREO_TALLER, carrito);
        PagoEntity pago = pagos.values().stream().filter(x -> x.getReferencia().equals(link.get("referencia"))).findFirst().orElseThrow();
        pagoMarketplace.confirmar(pago, "txn-" + pago.getId());

        PedidoTienda pedido = pedidos.values().stream().filter(x -> pago.getId().equals(x.getPagoId())).findFirst().orElseThrow();
        assertEquals("t1", pedido.getCompradorTallerId());
        pedido.setEstado(PedidoTienda.RETIRADO);
        inventarioTaller.confirmarLlegada(CORREO_TALLER, pedido.getId());
        pedido.setFechaEntradaInventario(LocalDateTime.now());
    }

    private static CotizacionItem itemTaller(int cantidad, double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).origen(CotizacionItem.ORIGEN_TALLER).piezaTallerId(PIEZA)
                .productoNombre("Pastillas").disponibilidad(OfertaRepuestoDTO.DISPONIBLE).cantidad(cantidad).precioUnitario(precio).build();
    }

    private static CotizacionItem itemTienda(int cantidad, double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).productoId("p1").productoNombre("Pastillas").tiendaId("s1")
                .tiendaNombre("Repuestos Sur").disponibilidad(OfertaRepuestoDTO.DISPONIBLE).cantidad(cantidad).precioUnitario(precio).build();
    }

    private static CotizacionItem manoDeObra(double valor) {
        return CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).modalidad(CotizacionItem.POR_ITEM).descripcion("Instalación")
                .cantidad(1).precioUnitario(valor).build();
    }

    /** Una cotización aprobada, con sus totales de la calculadora real, y el pago del cliente aplicado. */
    private PagoEntity elClientePaga(CotizacionItem... items) {
        List<CotizacionItem> lista = List.of(items);
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(lista, 19, 19, 0, 10);
        Cotizacion c = Cotizacion.builder().id("c1").ordenId("o1").tallerId("t1").tallerNombre("AutoMax").usuarioId("cl1").version(1)
                .estado(Cotizacion.APROBADA).items(lista).subtotalRepuestos(t.subtotalRepuestos())
                .subtotalRepuestosTaller(t.subtotalRepuestosTaller()).subtotalManoObra(t.subtotalManoObra()).subtotal(t.subtotal())
                .ivaRepuestosPct(19.0).ivaManoObraPct(19.0).ivaRepuestos(t.ivaRepuestos()).ivaRepuestosTaller(t.ivaRepuestosTaller())
                .ivaManoObra(t.ivaManoObra()).iva(t.iva()).total(t.total()).anticipoDiagnostico(0.0).totalAPagar(t.totalAPagar()).build();
        cotizaciones.put("c1", c);
        OrdenServicio orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE).estado(EstadoOrden.APROBADA)
                .tallerId("t1").usuarioId("cl1").cotizacionId("c1").vehiculoDescripcion("Spark").build();
        ordenes.put("o1", orden);
        PagoEntity pago = new PagoEntity();
        pago.setId(++secuencia);
        pago.setReferencia("COT-" + pago.getId());
        pago.setConcepto(PagoMarketplaceService.COTIZACION);
        pago.setOrdenServicioId("o1");
        pago.setCotizacionId("c1");
        pago.setUsuarioId("cl1");
        pago.setMonto(t.totalAPagar());
        pago.setEstado("PENDIENTE");
        pagos.put(pago.getId(), pago);
        pagoMarketplace.confirmar(pago, "txn-" + pago.getId());
        return pago;
    }

    private int stockTienda() {
        return productos.get("p1").getStockActual();
    }

    private RepartoPago lineaTaller(PagoEntity pago) {
        return lineas.stream().filter(r -> pago.getId().equals(r.getPagoId()) && RepartoPago.TALLER.equals(r.getBeneficiarioTipo())).findFirst().orElseThrow();
    }

    private List<PedidoTienda> pedidosDe(PagoEntity pago) {
        return pedidos.values().stream().filter(p -> pago.getId().equals(p.getPagoId())).toList();
    }

    // ─── Escenarios de aceptación ────────────────────────────────────────────

    @Test
    void escenarioA_compra_5_recibe_5_vende_1() {
        elTallerCompraYRecibe(5);
        assertEquals(15, stockTienda(), "La tienda baja por la venta de 5");
        assertEquals(5, inventario.stock.disponible("t1", PIEZA));

        PagoEntity pago = elClientePaga(itemTaller(1, 135_000), manoDeObra(50_000));

        assertEquals(4, inventario.stock.disponible("t1", PIEZA));
        assertEquals(15, stockTienda(), "Vender la pieza del taller no toca la tienda");
        assertEquals(1, movimientosTienda.size(), "Solo la venta original de 5 movió su stock");
        assertTrue(pedidosDe(pago).isEmpty(), "Lo del taller no genera pedido");
        assertEquals(EstadoOrden.REPUESTOS_RECIBIDOS, ordenes.get("o1").getEstado());
    }

    @Test
    void escenarioB_tiene_2_necesita_5_mezcla_2_propias_y_3_de_tienda() {
        elTallerCompraYRecibe(2);
        assertEquals(18, stockTienda());

        PagoEntity pago = elClientePaga(itemTaller(2, 135_000), itemTienda(3, 100_000), manoDeObra(50_000));

        assertEquals(0, inventario.stock.disponible("t1", PIEZA));
        List<PedidoTienda> aTienda = pedidosDe(pago);
        assertEquals(1, aTienda.size());
        assertEquals("s1", aTienda.get(0).getTiendaId());
        assertEquals(1, aTienda.get(0).getItems().size());
        assertEquals(3, aTienda.get(0).getItems().get(0).getCantidad());
        assertEquals(15, stockTienda(), "A la tienda solo le bajan las 3 del pedido");
        assertEquals(EstadoOrden.REPUESTOS_PEDIDOS, ordenes.get("o1").getEstado());
    }

    @Test
    void escenarioC_paga_se_cancela_antes_de_instalar_y_todo_vuelve_a_su_lugar() {
        elTallerCompraYRecibe(2);
        elTallerCompraYRecibe(3);
        Map<String, Integer> lotesAntes = inventario.disponiblePorLote(PIEZA);
        assertEquals(15, stockTienda());

        PagoEntity pago = elClientePaga(itemTaller(4, 135_000), itemTienda(1, 100_000), manoDeObra(50_000));
        assertEquals(1, inventario.stock.disponible("t1", PIEZA));
        assertEquals(14, stockTienda());
        PedidoTienda aTienda = pedidosDe(pago).get(0);

        CancelacionOrdenService.Resultado r = cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO);

        // El pago se revierte: queda el reembolso completo y ninguna línea por pagar
        assertEquals(pago.getMonto(), r.reembolso());
        assertEquals(1, reembolsos.size());
        assertEquals(pago.getMonto(), reembolsos.get(0).getMonto());
        assertEquals(Reembolso.PENDIENTE, reembolsos.get(0).getEstado());
        assertEquals(RepartoPago.ANULADO, lineaTaller(pago).getEstado());
        // El pedido se cancela porque no se había despachado y la tienda recupera su unidad
        assertEquals(PedidoTienda.NO_DESPACHADO, aTienda.getEstado());
        assertEquals(15, stockTienda());
        // El inventario recupera exactamente lo que salió de cada lote
        assertEquals(lotesAntes, inventario.disponiblePorLote(PIEZA));
        assertEquals(5, inventario.stock.disponible("t1", PIEZA));
        assertEquals(EstadoOrden.CANCELADA, ordenes.get("o1").getEstado());
        assertEquals(Cotizacion.ANULADA, cotizaciones.get("c1").getEstado());
    }

    // ─── Riesgos ─────────────────────────────────────────────────────────────

    @Test
    void riesgo1_una_pieza_del_taller_con_productoId_de_la_tienda_tampoco_toca_su_stock_ni_crea_pedido() {
        elTallerCompraYRecibe(2);
        CotizacionItem conProducto = itemTaller(1, 135_000);
        conProducto.setProductoId("p1");

        PagoEntity pago = elClientePaga(conProducto, manoDeObra(50_000));

        assertEquals(18, stockTienda());
        assertTrue(pedidosDe(pago).isEmpty());
    }

    @Test
    void riesgo3_cada_salida_queda_con_su_lote_y_su_costo() {
        elTallerCompraYRecibe(3);
        PagoEntity pago = elClientePaga(itemTaller(2, 135_000));

        MovimientoPiezaTaller salida = inventario.movimientos.get(MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.SALIDA, PIEZA, "PAGO-" + pago.getReferencia()));

        assertEquals(1, salida.getLotes().size());
        assertEquals(2, salida.getLotes().get(0).getCantidad());
        assertEquals(100_000, salida.getLotes().get(0).getCostoUnitario());
        assertEquals(pago.getId(), salida.getPagoId());
        assertEquals("o1", salida.getOrdenId());
    }

    @Test
    void riesgo5_la_compra_y_la_venta_generan_cada_una_su_comision_y_su_registro() {
        elTallerCompraYRecibe(1);
        RepartoPago lineaTienda = lineas.stream().filter(r -> RepartoPago.TIENDA.equals(r.getBeneficiarioTipo())).findFirst().orElseThrow();
        RepartoPago comisionCompra = lineas.stream().filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).findFirst().orElseThrow();
        assertEquals(10_000, lineaTienda.getComisionRetenida(), "10 % de 100.000 sin IVA");
        assertEquals(109_000, lineaTienda.getMonto(), "119.000 con IVA menos la comisión");
        assertEquals(10_000, comisionCompra.getMonto());

        PagoEntity venta = elClientePaga(itemTaller(1, 135_000), manoDeObra(50_000));

        RepartoPago lineaVenta = lineaTaller(venta);
        RepartoPago comisionVenta = lineas.stream().filter(r -> venta.getId().equals(r.getPagoId())
                && RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).findFirst().orElseThrow();
        assertNotEquals(comisionCompra.getPagoId(), comisionVenta.getPagoId());
        assertEquals(185_000, lineaVenta.getComisionBase());
        assertEquals(18_500, lineaVenta.getComisionRetenida());
        assertEquals(18_500, comisionVenta.getMonto());
        assertEquals(RepartoPago.RETENIDA, comisionVenta.getEstado());
        assertEquals(220_150, lineaVenta.getMontoBruto(), "(135.000 + 50.000) + 19 %");
        assertEquals(201_650, lineaVenta.getMonto());
        // Las piezas de la línea, aparte de la mano de obra
        assertEquals(135_000, lineaVenta.getPiezasBase());
        assertEquals(160_650, lineaVenta.getPiezasBruto());
        assertEquals(147_150, lineaVenta.getPiezasNeto());
    }

    @Test
    void sin_retencion_el_taller_recibe_todo_y_la_comision_solo_queda_calculada() {
        elTallerCompraYRecibe(1);
        when(parametros.comisionRetenida()).thenReturn(false);

        PagoEntity venta = elClientePaga(itemTaller(1, 135_000), manoDeObra(50_000));

        RepartoPago linea = lineaTaller(venta);
        assertEquals(220_150, linea.getMonto());
        assertEquals(18_500, linea.getComisionCalculada());
        assertEquals(0, linea.getComisionRetenida());
        assertEquals(160_650, linea.getPiezasNeto());
    }

    @Test
    void lo_que_esta_en_camino_no_alcanza_para_vender() {
        CompraRequest carrito = new CompraRequest(List.of(new CompraRequest.Item("p1", 5)),
                List.of(new CompraRequest.Entrega("s1", PedidoTienda.RETIRO)), null, null);
        Map<String, Object> link = compra.iniciarPago(CORREO_TALLER, carrito);
        PagoEntity compraPago = pagos.values().stream().filter(x -> x.getReferencia().equals(link.get("referencia"))).findFirst().orElseThrow();
        pagoMarketplace.confirmar(compraPago, "txn");
        assertEquals(0, inventario.stock.disponible("t1", PIEZA));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                new BuscadorRepuestosService(null, null, null, null, null, inventario.piezaRepository)
                        .ofertaDePieza(PIEZA, 1, taller, new com.drivemaster.drivemaster.model.Vehiculo()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void si_el_stock_no_alcanza_al_confirmar_no_se_aplica_y_todo_vuelve() {
        elTallerCompraYRecibe(1);
        PagoEntity pago = elClientePaga(itemTaller(2, 135_000), itemTienda(1, 100_000));

        assertEquals(1, inventario.stock.disponible("t1", PIEZA));
        assertEquals(19, stockTienda());
        assertTrue(pedidosDe(pago).isEmpty());
        assertTrue(lineas.stream().noneMatch(r -> pago.getId().equals(r.getPagoId())));
        assertEquals(1, reembolsos.size());
        assertEquals(pago.getMonto(), reembolsos.get(0).getMonto());
        verify(avisos).piezaTallerSinStock(any(), eq("Pastillas"));
        assertEquals(EstadoOrden.DIAGNOSTICADA, ordenes.get("o1").getEstado());
    }

    @Test
    void si_falta_stock_de_la_tienda_las_piezas_del_taller_vuelven_a_su_lote() {
        elTallerCompraYRecibe(3);
        Map<String, Integer> antes = inventario.disponiblePorLote(PIEZA);
        productos.get("p1").setStockActual(0);

        elClientePaga(itemTaller(2, 135_000), itemTienda(1, 100_000));

        assertEquals(antes, inventario.disponiblePorLote(PIEZA));
        assertEquals(1, reembolsos.size());
    }

    @Test
    void cancelar_exige_motivo_y_estado_y_no_se_hace_dos_veces() {
        elTallerCompraYRecibe(2);
        PagoEntity pago = elClientePaga(itemTaller(1, 135_000));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> cancelacion.cancelar(CORREO_ADMIN, "o1", "corto")).getStatusCode());
        assertEquals(1, inventario.stock.disponible("t1", PIEZA));

        cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO)).getStatusCode());

        assertEquals(1, reembolsos.size());
        assertEquals(2, inventario.stock.disponible("t1", PIEZA));
        assertEquals(pago.getMonto(), reembolsos.get(0).getMonto());
    }

    @Test
    void despues_de_iniciar_la_instalacion_no_hay_cancelacion() {
        elTallerCompraYRecibe(2);
        elClientePaga(itemTaller(1, 135_000));
        ordenes.get("o1").setEstado(EstadoOrden.EN_INSTALACION);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO)).getStatusCode());

        assertTrue(reembolsos.isEmpty());
        assertEquals(1, inventario.stock.disponible("t1", PIEZA));
    }

    @Test
    void lo_que_la_tienda_ya_despacho_no_se_reembolsa_y_sigue_su_curso() {
        elTallerCompraYRecibe(2);
        PagoEntity pago = elClientePaga(itemTaller(1, 135_000), itemTienda(1, 100_000), manoDeObra(50_000));
        PedidoTienda despachado = pedidosDe(pago).get(0);
        despachado.setEstado(PedidoTienda.DESPACHADO);
        RepartoPago lineaTienda = lineas.stream().filter(r -> despachado.getId().equals(r.getPedidoTiendaId())).findFirst().orElseThrow();

        CancelacionOrdenService.Resultado r = cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO);

        assertEquals(List.of("Repuestos Sur"), r.pedidosYaDespachados());
        assertEquals(pago.getMonto() - despachado.getTotal(), r.reembolso());
        assertEquals(PedidoTienda.DESPACHADO, despachado.getEstado());
        assertEquals(RepartoPago.PENDIENTE, lineaTienda.getEstado());
        assertEquals(17, stockTienda(), "La unidad despachada no vuelve a la tienda");
        verify(avisos).alertaAdmin(eq("Cancelación con pedidos despachados"), anyString());
    }

    @Test
    void un_pedido_que_ya_se_reembolso_no_se_devuelve_dos_veces() {
        elTallerCompraYRecibe(2);
        PagoEntity pago = elClientePaga(itemTaller(1, 135_000), itemTienda(1, 100_000));
        PedidoTienda aTienda = pedidosDe(pago).get(0);
        aTienda.setEstado(PedidoTienda.NO_DESPACHADO);
        pagoMarketplace.registrarReembolso(pago, aTienda.getId(), aTienda.getTotal(), "La tienda no despachó");

        CancelacionOrdenService.Resultado r = cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO);

        assertEquals(pago.getMonto() - aTienda.getTotal(), r.reembolso());
        assertEquals(pago.getMonto(), reembolsos.stream().mapToDouble(Reembolso::getMonto).sum());
    }

    @Test
    void si_devolver_las_piezas_falla_el_reembolso_queda_igual_y_se_alerta() {
        elTallerCompraYRecibe(2);
        PagoEntity pago = elClientePaga(itemTaller(1, 135_000));
        when(inventario.movimientoRepository.findByReferenciaAndTipo(anyString(), anyString())).thenThrow(new IllegalStateException("Mongo caído"));

        cancelacion.cancelar(CORREO_ADMIN, "o1", MOTIVO);

        assertEquals(pago.getMonto(), reembolsos.get(0).getMonto());
        verify(avisos).alertaAdmin(eq("Cancelación incompleta"), contains("devolver las piezas"));
    }
}
