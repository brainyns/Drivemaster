package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Confirmación de pagos del marketplace: idempotencia, stock con reversa, pedidos
 * por tienda, reparto y reembolsos cuando el pago no se puede aplicar.
 */
class PagoMarketplaceServiceTest {

    private PagoService pagoService;
    private PagoRepository pagoRepository;
    private OrdenServicioRepository ordenRepository;
    private CotizacionRepository cotizacionRepository;
    private PedidoTiendaRepository pedidoRepository;
    private RepartoPagoRepository repartoRepository;
    private ReembolsoRepository reembolsoRepository;
    private ProductoService productoService;
    private MovimientoInventarioService movimientoService;
    private TransicionOrdenService transiciones;
    private CotizacionService cotizacionService;
    private AvisosSolicitudServicio avisos;
    private PagoMarketplaceService service;
    private ParametrosMarketplace parametros;
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private OrdenServicio orden;
    private Cotizacion cotizacion;

    @BeforeEach
    void setUp() {
        pagoService = mock(PagoService.class);
        pagoRepository = mock(PagoRepository.class);
        ordenRepository = mock(OrdenServicioRepository.class);
        cotizacionRepository = mock(CotizacionRepository.class);
        pedidoRepository = mock(PedidoTiendaRepository.class);
        repartoRepository = mock(RepartoPagoRepository.class);
        reembolsoRepository = mock(ReembolsoRepository.class);
        productoService = mock(ProductoService.class);
        movimientoService = mock(MovimientoInventarioService.class);
        transiciones = mock(TransicionOrdenService.class);
        cotizacionService = mock(CotizacionService.class);
        avisos = mock(AvisosSolicitudServicio.class);
        TallerRepository tallerRepository = mock(TallerRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);

        service = new PagoMarketplaceService(pagoService, pagoRepository, ordenRepository, cotizacionRepository,
                pedidoRepository, repartoRepository, reembolsoRepository, tallerRepository, productoService,
                movimientoService, mock(com.drivemaster.drivemaster.repository.MovimientoInventarioRepository.class), usuarioService, transiciones, cotizacionService, avisos,
                parametros = mock(ParametrosMarketplace.class));
        mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        service.setMongoTemplate(mongoTemplate);

        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE).estado(EstadoOrden.APROBADA)
                .tallerId("t1").usuarioId("u1").cotizacionId("c1").vehiculoDescripcion("Spark")
                .aceptadaPorTaller(true).build();
        when(ordenRepository.findById("o1")).thenAnswer(i -> Optional.of(orden));
        when(tallerRepository.findById("t1")).thenReturn(Optional.of(Taller.builder().id("t1").nombre("AutoMax")
                .direccion("Cra 1").ciudad("Bogotá").build()));
        when(usuarioService.obtenerPorCorreo("cliente@demo.com"))
                .thenReturn(Usuario.builder().id("u1").correo("cliente@demo.com").rol("CLIENTE").build());
        when(pagoRepository.reclamar(anyLong(), anyString())).thenReturn(1);
        when(transiciones.transicionar(any(), anyString(), any(), anyString(), any(), any())).thenAnswer(i -> {
            OrdenServicio o = i.getArgument(0);
            o.setEstado(i.getArgument(1));
            return o;
        });
        when(pedidoRepository.save(any())).thenAnswer(i -> {
            PedidoTienda p = i.getArgument(0);
            p.setId("ped-" + p.getTiendaId());
            return p;
        });
    }

    private static CotizacionItem repuesto(String producto, String tienda, int cantidad, double subtotal, String disponibilidad) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).productoId(producto).productoNombre(producto)
                .tiendaId(tienda).tiendaNombre("Tienda " + tienda).cantidad(cantidad).subtotal(subtotal)
                .disponibilidad(disponibilidad).build();
    }

    private void cotizacion(List<CotizacionItem> items, double totalAPagar) {
        cotizacion = Cotizacion.builder().id("c1").ordenId("o1").version(1).estado(Cotizacion.APROBADA)
                .tallerNombre("AutoMax").ivaPct(19.0).items(items).iva(0.0).totalAPagar(totalAPagar)
                .vigenciaHasta(LocalDateTime.now().plusDays(2)).build();
        when(cotizacionRepository.findById("c1")).thenReturn(Optional.of(cotizacion));
    }

    private static PagoEntity pago(String concepto, double monto) {
        PagoEntity p = new PagoEntity();
        p.setId(10L);
        p.setConcepto(concepto);
        p.setOrdenServicioId("o1");
        p.setCotizacionId(PagoMarketplaceService.COTIZACION.equals(concepto) ? "c1" : null);
        p.setReferencia("DRV-REF");
        p.setMonto(monto);
        p.setEstado("PENDIENTE");
        return p;
    }

    // ─── Idempotencia ────────────────────────────────────────────────────────

    @Test
    void unPagoYaTomadoNoSeAplicaDosVeces() {
        when(pagoRepository.reclamar(anyLong(), anyString())).thenReturn(0);
        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 100_000), "txn-1");
        verifyNoInteractions(transiciones, movimientoService, repartoRepository, reembolsoRepository);
        verify(pagoRepository, never()).save(any());
    }

    // ─── Diagnóstico ─────────────────────────────────────────────────────────

    @Test
    void diagnosticoPagadoPasaAEnRevisionYRepartoAlTaller() {
        orden.setEstado(EstadoOrden.ASIGNADA_A_TALLER);
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PENDIENTE);
        PagoEntity pago = pago(PagoMarketplaceService.DIAGNOSTICO, 50_000);

        service.confirmar(pago, "txn-1");

        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.EN_REVISION), eq(TransicionOrdenService.Actor.SISTEMA),
                anyString(), any(), any());
        ArgumentCaptor<RepartoPago> reparto = ArgumentCaptor.forClass(RepartoPago.class);
        verify(repartoRepository).save(reparto.capture());
        assertEquals(RepartoPago.TALLER, reparto.getValue().getBeneficiarioTipo());
        assertEquals(50_000, reparto.getValue().getMonto());
        assertEquals("PAGADO", pago.getEstado());
        assertEquals("txn-1", pago.getTransactionId());
        verify(pagoRepository).save(pago);
        verify(avisos).comprobantePago(eq(orden), eq(pago), anyString(), anyMap(), anyString());
    }

    @Test
    void diagnosticoPagadoTardeQuedaComoReembolso() {
        orden.setEstado(EstadoOrden.CANCELADA);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "No se puede"))
                .when(transiciones).transicionar(any(), anyString(), any(), anyString(), any(), any());
        PagoEntity pago = pago(PagoMarketplaceService.DIAGNOSTICO, 50_000);

        service.confirmar(pago, "txn-1");

        ArgumentCaptor<Reembolso> r = ArgumentCaptor.forClass(Reembolso.class);
        verify(reembolsoRepository).save(r.capture());
        assertEquals(50_000, r.getValue().getMonto());
        verify(repartoRepository, never()).save(any());
        assertEquals("PAGADO", pago.getEstado());
        verify(avisos).pagoNoAplicado(eq(orden), eq(50_000.0), anyString());
    }

    // ─── Cotización ──────────────────────────────────────────────────────────

    @Test
    void cotizacionPagadaDescuentaStockCreaPedidosYReparte() {
        // Tienda s1: 90.000 en stock; tienda s2: 30.000 por encargo (no descuenta stock).
        // Mano de obra 100.000 + IVA 19.000 − anticipo 50.000 = 69.000 para el taller.
        cotizacion(new ArrayList<>(List.of(
                repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"),
                repuesto("p2", "s2", 1, 30_000, "ENCARGO"),
                CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).subtotal(100_000.0).build())), 211_800);
        PagoEntity pago = pago(PagoMarketplaceService.COTIZACION, 211_800);

        service.confirmar(pago, "txn-1");

        verify(movimientoService).registrarMovimiento(eq("p1"), eq("SALIDA"), eq(2), anyString(), eq("PAGO-DRV-REF"), any());
        verify(movimientoService, never()).registrarMovimiento(eq("p2"), anyString(), anyInt(), anyString(), anyString(), any());
        verify(pedidoRepository, times(2)).save(any());
        ArgumentCaptor<RepartoPago> repartos = ArgumentCaptor.forClass(RepartoPago.class);
        verify(repartoRepository, times(3)).save(repartos.capture());
        List<RepartoPago> lista = repartos.getAllValues();
        assertEquals(107_100, lista.get(0).getMonto()); // 90.000 + IVA 17.100
        assertEquals(35_700, lista.get(1).getMonto());  // 30.000 + IVA 5.700
        assertEquals(RepartoPago.TALLER, lista.get(2).getBeneficiarioTipo());
        assertEquals(69_000, lista.get(2).getMonto());
        assertEquals(211_800, lista.stream().mapToDouble(RepartoPago::getMonto).sum());

        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.PAGADA), any(), anyString(), any(), any());
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.REPUESTOS_PEDIDOS), any(), anyString(), any(), any());
        verify(avisos).cotizacionPagada(eq(orden), argThat(l -> l.size() == 2));
        verify(reembolsoRepository, never()).save(any());
    }

    // ─── Comisión informativa de la plataforma ───────────────────────────────

    private List<RepartoPago> lineasGuardadas(int cuantas) {
        ArgumentCaptor<RepartoPago> captor = ArgumentCaptor.forClass(RepartoPago.class);
        verify(repartoRepository, times(cuantas)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void elDiagnosticoPagadoRegistraLaComisionSobreLaTarifaConIva() {
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.ivaPct()).thenReturn(19.0);
        orden.setEstado(EstadoOrden.ASIGNADA_A_TALLER);
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PENDIENTE);

        service.confirmar(pago(PagoMarketplaceService.DIAGNOSTICO, 50_000), "txn-1");

        List<RepartoPago> lineas = lineasGuardadas(2);
        assertEquals(RepartoPago.TALLER, lineas.get(0).getBeneficiarioTipo());
        assertEquals(50_000, lineas.get(0).getMonto(), "el taller recibe el pago completo: la comisión no se descuenta");
        RepartoPago comision = lineas.get(1);
        assertEquals(RepartoPago.PLATAFORMA, comision.getBeneficiarioTipo());
        assertEquals(RepartoPago.INFORMATIVA, comision.getEstado());
        assertEquals(5_000, comision.getMonto()); // 10 % de los 50.000 pagados, IVA incluido
    }

    @Test
    void laCotizacionPagadaRegistraLaComisionSobreLoPagadoConIva() {
        when(parametros.comisionPct()).thenReturn(10.0);
        cotizacion(new ArrayList<>(List.of(
                repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"),
                CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).subtotal(100_000.0).build())), 211_800);

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 211_800), "txn-1");

        List<RepartoPago> lineas = lineasGuardadas(3);
        RepartoPago comision = lineas.get(lineas.size() - 1);
        assertEquals(RepartoPago.PLATAFORMA, comision.getBeneficiarioTipo());
        assertEquals(21_180, comision.getMonto()); // 10 % de los 211.800 pagados, IVA incluido
        assertNull(comision.getPedidoTiendaId());
    }

    @Test
    void elReembolsoDeUnaTiendaAnulaSoloSuParteDeLaComision() {
        PagoEntity pago = pago(PagoMarketplaceService.COTIZACION, 100_000);
        pago.setId(10L);
        when(reembolsoRepository.save(any(Reembolso.class))).thenAnswer(i -> {
            Reembolso r = i.getArgument(0);
            r.setId(55L);
            return r;
        });
        when(repartoRepository.sumaComisionDePago(10L)).thenReturn(8_000.0);
        when(repartoRepository.sumaComisionBruta(10L)).thenReturn(8_000.0);

        service.registrarReembolso(pago, "ped-s1", 25_000, "No despachó");

        ArgumentCaptor<RepartoPago> linea = ArgumentCaptor.forClass(RepartoPago.class);
        verify(repartoRepository).save(linea.capture());
        assertEquals(RepartoPago.PLATAFORMA, linea.getValue().getBeneficiarioTipo());
        assertEquals(-2_000, linea.getValue().getMonto()); // 25% del pago = 25% de la comisión
        assertEquals("REEMBOLSO-55", linea.getValue().getReferenciaLiquidacion());
    }

    @Test
    void siFallaLaAnulacionElReembolsoSeRegistraIgual() {
        PagoEntity pago = pago(PagoMarketplaceService.COTIZACION, 100_000);
        Reembolso guardado = Reembolso.builder().id(1L).build();
        when(reembolsoRepository.save(any(Reembolso.class))).thenReturn(guardado);
        when(repartoRepository.sumaComisionDePago(anyLong())).thenThrow(new RuntimeException("caída"));

        assertSame(guardado, service.registrarReembolso(pago, null, 100_000, "Pago no aplicado"));
    }

    @Test
    void unPagoNoAplicadoNoRegistraComision() {
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.ivaPct()).thenReturn(19.0);
        orden.setEstado(EstadoOrden.CANCELADA); // ya no esperaba el pago: se reembolsa
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_VENCIDO);
        when(transiciones.transicionar(any(), eq(EstadoOrden.EN_REVISION), any(), anyString(), any(), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "x"));

        service.confirmar(pago(PagoMarketplaceService.DIAGNOSTICO, 50_000), "txn-1");

        verify(repartoRepository, never()).save(any());
        verify(reembolsoRepository).save(any());
    }

    @Test
    void sinStockSeRevierteElDescuentoYSeReembolsa() {
        cotizacion(new ArrayList<>(List.of(
                repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"),
                repuesto("p2", "s1", 1, 30_000, "DISPONIBLE"))), 142_800);
        when(movimientoService.registrarMovimiento(eq("p2"), eq("SALIDA"), eq(1), anyString(), anyString(), any()))
                .thenThrow(new StockInsuficienteException("p2", 0));
        PagoEntity pago = pago(PagoMarketplaceService.COTIZACION, 142_800);

        service.confirmar(pago, "txn-1");

        verify(movimientoService).registrarMovimiento(eq("p1"), eq("ENTRADA"), eq(2), anyString(), eq("PAGO-DRV-REF"), any());
        verify(pedidoRepository, never()).save(any());
        verify(repartoRepository, never()).save(any());
        verify(reembolsoRepository).save(argThat(r -> r.getMonto() == 142_800 && r.getMotivo().contains("p2")));
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.DIAGNOSTICADA), any(), anyString(), any(), any());
        assertEquals(Cotizacion.ANULADA, cotizacion.getEstado());
        assertEquals("PAGADO", pago.getEstado());
    }

    /** La orden se paga y se crean las líneas, pero falla el último paso: pasar a REPUESTOS_PEDIDOS. */
    private void fallaTrasCrearLasLineas() {
        doAnswer(i -> {
            if (EstadoOrden.REPUESTOS_PEDIDOS.equals(i.getArgument(1))) throw new IllegalStateException("Mongo no responde");
            OrdenServicio o = i.getArgument(0);
            o.setEstado(i.getArgument(1));
            return o;
        }).when(transiciones).transicionar(any(), anyString(), any(), anyString(), any(), any());
    }

    private static RepartoPago lineaDelPago(long id, String tipo, String estado) {
        return RepartoPago.builder().id(id).pagoId(10L).beneficiarioTipo(tipo).estado(estado).monto(100.0).build();
    }

    @Test
    void siFallaTrasCrearLasLineasQuedanAnuladasConElReembolso() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        fallaTrasCrearLasLineas();
        when(repartoRepository.findByPagoId(10L)).thenReturn(List.of(
                lineaDelPago(1L, RepartoPago.TIENDA, RepartoPago.PENDIENTE),
                lineaDelPago(2L, RepartoPago.TALLER, RepartoPago.PENDIENTE),
                lineaDelPago(3L, RepartoPago.PLATAFORMA, RepartoPago.INFORMATIVA)));

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(repartoRepository).anularPendiente(eq(1L), anyString());
        verify(repartoRepository).anularPendiente(eq(2L), anyString());
        verify(repartoRepository, never()).anularPendiente(eq(3L), anyString());
        ArgumentCaptor<Reembolso> r = ArgumentCaptor.forClass(Reembolso.class);
        verify(reembolsoRepository).save(r.capture());
        assertEquals(107_100, r.getValue().getMonto());
    }

    private org.mockito.stubbing.OngoingStubbing<PedidoTienda> alCancelarUnPedido() {
        return when(mongoTemplate.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(PedidoTienda.class)));
    }

    private void pedidosDelPago() {
        when(pedidoRepository.findByPagoId(10L)).thenReturn(List.of(
                PedidoTienda.builder().id("ped-s1").tiendaId("s1").tiendaNombre("Tienda s1").build()));
    }

    @Test
    void siFallaTrasCrearLosPedidosQuedanCanceladosYElStockVuelve() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"), repuesto("p2", "s1", 1, 10_000, "ENCARGO"))), 119_000);
        fallaTrasCrearLasLineas();
        pedidosDelPago();
        alCancelarUnPedido().thenReturn(new PedidoTienda());

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 119_000), "txn-1");

        ArgumentCaptor<org.springframework.data.mongodb.core.query.Query> filtro =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        ArgumentCaptor<org.springframework.data.mongodb.core.query.Update> cambio =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongoTemplate).findAndModify(filtro.capture(), cambio.capture(),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(PedidoTienda.class));
        org.bson.Document puestos = (org.bson.Document) cambio.getValue().getUpdateObject().get("$set");
        assertEquals(PedidoTienda.NO_DESPACHADO, puestos.get("estado"));
        assertEquals(PedidoTienda.MOTIVO_PAGO_NO_APLICADO, puestos.get("tipoMotivoNoDespacho"));
        assertTrue(filtro.getValue().getQueryObject().toJson().contains(PedidoTienda.PENDIENTE), "solo si sigue sin despachar");
        assertFalse(filtro.getValue().getQueryObject().toJson().contains(PedidoTienda.DESPACHADO + "\""));

        verify(movimientoService).registrarMovimiento(eq("p1"), eq("ENTRADA"), eq(2), anyString(), eq("PAGO-DRV-REF"), any());
        verify(movimientoService, never()).registrarMovimiento(eq("p2"), anyString(), anyInt(), anyString(), anyString(), any());
        verify(reembolsoRepository).save(any());
        verify(avisos).alertaAdmin(eq("Pago sin aplicar"), anyString());
    }

    @Test
    void siFallaTrasPagarLaOrdenVuelveADiagnosticadaConLaCotizacionAnulada() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        fallaTrasCrearLasLineas();

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        assertEquals(EstadoOrden.DIAGNOSTICADA, orden.getEstado(), "no queda PAGADA con el dinero devuelto");
        assertEquals(Cotizacion.ANULADA, cotizacion.getEstado());
        verify(cotizacionRepository).save(cotizacion);
        ArgumentCaptor<org.springframework.data.mongodb.core.query.Update> cambio =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.DIAGNOSTICADA), any(), anyString(), any(), cambio.capture());
        assertTrue(cambio.getValue().getUpdateObject().toJson().contains("pagoCotizacionId"), "sin el pago que no se aplicó");
        verify(avisos).pagoNoAplicado(eq(orden), eq(107_100.0), anyString());
        verify(reembolsoRepository).save(any());
    }

    @Test
    void siLaOrdenNoPuedeVolverSeAlertaYElReembolsoSigue() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        doAnswer(i -> {
            String destino = i.getArgument(1);
            if (!EstadoOrden.PAGADA.equals(destino)) throw new IllegalStateException("Mongo no responde");
            OrdenServicio o = i.getArgument(0);
            o.setEstado(destino);
            return o;
        }).when(transiciones).transicionar(any(), anyString(), any(), anyString(), any(), any());

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(avisos).alertaAdmin(eq("Orden sin corregir"), contains("o1"));
        verify(reembolsoRepository).save(any());
        verify(movimientoService).registrarMovimiento(eq("p1"), eq("ENTRADA"), eq(2), anyString(), eq("PAGO-DRV-REF"), any());
    }

    private PagoEntity diagnosticoQueFalla() {
        orden.setEstado(EstadoOrden.ASIGNADA_A_TALLER);
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PENDIENTE);
        when(parametros.timeoutPagoDiagnosticoMin()).thenReturn(1440);
        when(repartoRepository.save(any())).thenThrow(new IllegalStateException("La base no responde"));
        return pago(PagoMarketplaceService.DIAGNOSTICO, 59_500);
    }

    @Test
    void siFallaElDiagnosticoLaOrdenVuelveAEsperarElPagoConPlazoNuevo() {
        LocalDateTime antes = LocalDateTime.now();

        service.confirmar(diagnosticoQueFalla(), "txn-1");

        assertEquals(EstadoOrden.ASIGNADA_A_TALLER, orden.getEstado(), "no queda EN_REVISION con el dinero devuelto");
        ArgumentCaptor<org.springframework.data.mongodb.core.query.Update> cambio =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.ASIGNADA_A_TALLER), any(), anyString(), any(), cambio.capture());
        org.bson.Document puestos = (org.bson.Document) cambio.getValue().getUpdateObject().get("$set");
        assertEquals(EstadoOrden.PAGO_PENDIENTE, puestos.get("pagoDiagnosticoEstado"));
        assertTrue(((LocalDateTime) puestos.get("fechaLimitePagoDiagnostico")).isAfter(antes.plusMinutes(1439)));
        verify(avisos).pagoNoAplicado(eq(orden), eq(59_500.0), anyString());
        verify(reembolsoRepository).save(any());
    }

    @Test
    void elPlazoNuevoDelDiagnosticoNuncaPasaDeLaCita() {
        LocalDateTime cita = LocalDateTime.now().plusHours(2);
        orden.setFechaProgramada(cita);

        service.confirmar(diagnosticoQueFalla(), "txn-1");

        ArgumentCaptor<org.springframework.data.mongodb.core.query.Update> cambio =
                ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(transiciones).transicionar(any(), eq(EstadoOrden.ASIGNADA_A_TALLER), any(), anyString(), any(), cambio.capture());
        assertEquals(cita, ((org.bson.Document) cambio.getValue().getUpdateObject().get("$set")).get("fechaLimitePagoDiagnostico"));
    }

    @Test
    void siLaOrdenDelDiagnosticoNoPuedeVolverSeAlertaYElReembolsoSigue() {
        PagoEntity pago = diagnosticoQueFalla();
        doAnswer(i -> {
            String destino = i.getArgument(1);
            if (!EstadoOrden.EN_REVISION.equals(destino)) throw new IllegalStateException("Mongo no responde");
            OrdenServicio o = i.getArgument(0);
            o.setEstado(destino);
            return o;
        }).when(transiciones).transicionar(any(), anyString(), any(), anyString(), any(), any());

        service.confirmar(pago, "txn-1");

        verify(avisos).alertaAdmin(eq("Orden sin corregir"), contains("EN_REVISION"));
        verify(reembolsoRepository).save(any());
    }

    @Test
    void unAvisoQueFallaEnElDiagnosticoNoReembolsaYAlertaAlAdmin() {
        orden.setEstado(EstadoOrden.ASIGNADA_A_TALLER);
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PENDIENTE);
        doThrow(new IllegalStateException("Sin plantilla")).when(avisos).diagnosticoPagado(any());

        service.confirmar(pago(PagoMarketplaceService.DIAGNOSTICO, 59_500), "txn-1");

        assertEquals(EstadoOrden.EN_REVISION, orden.getEstado());
        verify(reembolsoRepository, never()).save(any());
        verify(avisos).alertaAdmin(eq("Avisos sin enviar"), contains("DRV-REF"));
    }

    @Test
    void siTambienFallaLaAlertaElPagoSigueAplicado() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        doThrow(new IllegalStateException("Sin correo")).when(avisos).comprobantePago(any(), any(), anyString(), anyMap(), anyString());
        doThrow(new IllegalStateException("Sin nada")).when(avisos).alertaAdmin(anyString(), anyString());

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(reembolsoRepository, never()).save(any());
    }

    @Test
    void unCorreoQueFallaNoReembolsaUnPagoYaAplicado() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        doThrow(new IllegalStateException("Sin correo")).when(avisos).comprobantePago(any(), any(), anyString(), anyMap(), anyString());

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(reembolsoRepository, never()).save(any());
        verify(repartoRepository, never()).anularPendiente(anyLong(), anyString());
        verify(movimientoService, never()).registrarMovimiento(anyString(), eq("ENTRADA"), anyInt(), anyString(), anyString(), any());
        assertEquals(EstadoOrden.REPUESTOS_PEDIDOS, orden.getEstado());
        verify(avisos).alertaAdmin(eq("Avisos sin enviar"), contains("DRV-REF"));
    }

    @Test
    void unPedidoQueYaSalioNoSeCancelaYSeAlerta() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        fallaTrasCrearLasLineas();
        pedidosDelPago();
        alCancelarUnPedido().thenReturn(null);

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(avisos).alertaAdmin(eq("Pedido sin cancelar"), contains("ped-s1"));
        verify(reembolsoRepository).save(any());
    }

    @Test
    void siNoSePuedenCancelarLosPedidosElReembolsoYElStockSiguen() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        fallaTrasCrearLasLineas();
        pedidosDelPago();
        alCancelarUnPedido().thenThrow(new IllegalStateException("Mongo no responde"));

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(avisos).alertaAdmin(eq("Pedidos sin cancelar"), anyString());
        verify(movimientoService).registrarMovimiento(eq("p1"), eq("ENTRADA"), eq(2), anyString(), eq("PAGO-DRV-REF"), any());
        verify(reembolsoRepository).save(any());
    }

    @Test
    void siFallaAlMarcarLaOrdenPagadaElStockVuelveYNoHayPedidos() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        doThrow(new IllegalStateException("Mongo no responde"))
                .when(transiciones).transicionar(any(), anyString(), any(), anyString(), any(), any());

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(movimientoService).registrarMovimiento(eq("p1"), eq("ENTRADA"), eq(2), anyString(), eq("PAGO-DRV-REF"), any());
        verify(pedidoRepository, never()).save(any());
        verify(reembolsoRepository).save(any());
    }

    @Test
    void siNoSePuedenAnularLasLineasElReembolsoSeRegistraYSeAlerta() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 90_000, "DISPONIBLE"))), 107_100);
        fallaTrasCrearLasLineas();
        when(repartoRepository.findByPagoId(10L)).thenThrow(new IllegalStateException("La base no responde"));

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 107_100), "txn-1");

        verify(reembolsoRepository).save(any());
        verify(avisos).alertaAdmin(eq("Líneas sin anular"), anyString());
    }

    // ─── Comisión retenida ───────────────────────────────────────────────────

    /** Repuestos 200.000 + IVA 19 %, mano de obra 100.000 + IVA 19 % y diagnóstico de 59.500 ya pagado (base 50.000). */
    private PagoEntity cotizacionConDiagnostico(boolean retener) {
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionRetenida()).thenReturn(retener);
        orden.setIvaDiagnosticoPct(19.0);
        cotizacion = Cotizacion.builder().id("c1").ordenId("o1").version(1).estado(Cotizacion.APROBADA).tallerNombre("AutoMax")
                .ivaPct(19.0).ivaRepuestosPct(19.0).ivaManoObraPct(19.0)
                .items(new ArrayList<>(List.of(repuesto("p1", "s1", 2, 200_000, "ENCARGO"),
                        CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).subtotal(100_000.0).build())))
                .subtotalRepuestos(200_000.0).subtotalManoObra(100_000.0).subtotal(300_000.0)
                .ivaRepuestos(38_000.0).ivaManoObra(19_000.0).iva(57_000.0).total(357_000.0)
                .anticipoDiagnostico(59_500.0).totalAPagar(297_500.0).vigenciaHasta(LocalDateTime.now().plusDays(2)).build();
        when(cotizacionRepository.findById("c1")).thenReturn(Optional.of(cotizacion));
        return pago(PagoMarketplaceService.COTIZACION, 297_500);
    }

    @Test
    void laCotizacionRetieneACadaUnoSobreLoSuyoYLasLineasSumanLoPagado() {
        service.confirmar(cotizacionConDiagnostico(true), "txn-1");

        List<RepartoPago> lineas = lineasGuardadas(3);
        RepartoPago tienda = lineas.get(0);
        assertEquals(RepartoPago.TIENDA, tienda.getBeneficiarioTipo());
        assertEquals(238_000, tienda.getMontoBruto());
        assertEquals(238_000, tienda.getComisionBase(), "repuestos con su IVA");
        assertEquals(38_000, tienda.getIvaIncluido());
        assertEquals(23_800, tienda.getComisionRetenida());
        assertEquals(214_200, tienda.getMonto());

        RepartoPago taller = lineas.get(1);
        assertEquals(RepartoPago.TALLER, taller.getBeneficiarioTipo());
        assertEquals(59_500, taller.getMontoBruto());
        assertEquals(59_500, taller.getComisionBase(), "mano de obra con IVA menos el adelanto con IVA, que ya pagó comisión");
        assertEquals(9_500, taller.getIvaIncluido(), "19.000 de IVA de la mano de obra − 9.500 del adelanto");
        assertEquals(5_950, taller.getComisionRetenida());
        assertEquals(53_550, taller.getMonto());

        RepartoPago comision = lineas.get(2);
        assertEquals(RepartoPago.PLATAFORMA, comision.getBeneficiarioTipo());
        assertEquals(RepartoPago.RETENIDA, comision.getEstado());
        assertEquals(29_750, comision.getMonto());
        assertEquals(297_500, tienda.getMonto() + taller.getMonto() + comision.getMonto(), "lo pagado = netos + comisión");
    }

    @Test
    void comision_con_iva_cotizacion_con_adelanto_no_cobra_dos_veces_el_diagnostico() {
        service.confirmar(cotizacionConDiagnostico(true), "txn-1");

        List<RepartoPago> lineas = lineasGuardadas(3);
        // (300.000 + 57.000) − 59.500 del adelanto con IVA = 297.500, justo lo que paga el cliente en este pago
        assertEquals(297_500, lineas.get(0).getComisionBase() + lineas.get(1).getComisionBase());
        assertEquals(29_750, lineas.get(2).getMonto(), "10 % de 297.500");
        // Con el diagnóstico (5.950 sobre 59.500), DriveMaster cobra el 10 % de los 357.000 una sola vez
        assertEquals(35_700, lineas.get(2).getMonto() + 5_950);
    }

    @Test
    void conLaBanderaApagadaLaCotizacionPagaCompletoYLaComisionEsInformativa() {
        service.confirmar(cotizacionConDiagnostico(false), "txn-1");

        List<RepartoPago> lineas = lineasGuardadas(3);
        assertEquals(238_000, lineas.get(0).getMonto());
        assertEquals(23_800, lineas.get(0).getComisionCalculada());
        assertEquals(0, lineas.get(0).getComisionRetenida());
        assertEquals(59_500, lineas.get(1).getMonto());
        assertEquals(RepartoPago.INFORMATIVA, lineas.get(2).getEstado());
        assertEquals(29_750, lineas.get(2).getMonto());
    }

    @Test
    void comision_con_iva_diagnostico_sobre_la_tarifa_con_iva() {
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionRetenida()).thenReturn(true);
        orden.setEstado(EstadoOrden.ASIGNADA_A_TALLER);
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PENDIENTE);
        orden.setTarifaDiagnosticoBase(50_000.0);

        service.confirmar(pago(PagoMarketplaceService.DIAGNOSTICO, 59_500), "txn-1");

        List<RepartoPago> lineas = lineasGuardadas(2);
        assertEquals(59_500, lineas.get(0).getMontoBruto());
        assertEquals(59_500, lineas.get(0).getComisionBase(), "la tarifa con su IVA");
        assertEquals(9_500, lineas.get(0).getIvaIncluido());
        assertEquals(5_950, lineas.get(0).getComisionRetenida());
        assertEquals(53_550, lineas.get(0).getMonto());
        assertEquals(RepartoPago.RETENIDA, lineas.get(1).getEstado());
        assertEquals(5_950, lineas.get(1).getMonto());
    }

    // ─── Reembolsos con comisión retenida ────────────────────────────────────

    private static RepartoPago lineaRetenida(long id, String tipo, String pedido, String estado, double bruto, double retenida) {
        return RepartoPago.builder().id(id).pagoId(10L).beneficiarioTipo(tipo).pedidoTiendaId(pedido).estado(estado)
                .montoBruto(bruto).monto(bruto - retenida).comisionRetenida(retenida).build();
    }

    /** Tienda 238.000 (comisión 20.000) y taller sin IVA 100.000 (comisión 10.000): el reembolso ya no es proporcional. */
    private PagoEntity pagoConRetencion(String estadoTienda, String estadoTaller) {
        PagoEntity pago = pago(PagoMarketplaceService.COTIZACION, 338_000);
        when(reembolsoRepository.save(any(Reembolso.class))).thenAnswer(i -> {
            Reembolso r = i.getArgument(0);
            r.setId(55L);
            return r;
        });
        when(repartoRepository.findByPagoId(10L)).thenReturn(List.of(
                lineaRetenida(1L, RepartoPago.TIENDA, "ped-s1", estadoTienda, 238_000, 20_000),
                lineaRetenida(2L, RepartoPago.TALLER, null, estadoTaller, 100_000, 10_000),
                RepartoPago.builder().id(3L).pagoId(10L).beneficiarioTipo(RepartoPago.PLATAFORMA)
                        .estado(RepartoPago.RETENIDA).monto(30_000.0).build()));
        when(repartoRepository.sumaComisionDePago(10L)).thenReturn(30_000.0);
        when(repartoRepository.sumaComisionBruta(10L)).thenReturn(30_000.0);
        return pago;
    }

    @Test
    void elReembolsoParcialAnulaExactamenteLaComisionDeEsaLinea() {
        PagoEntity pago = pagoConRetencion(RepartoPago.ANULADO, RepartoPago.PENDIENTE);

        Reembolso reembolso = service.registrarReembolso(pago, "ped-s1", 238_000, "No despachó");

        RepartoPago anulacion = lineasGuardadas(1).get(0);
        assertEquals(-20_000, anulacion.getMonto(), "la proporción del dinero daría 21.124");
        assertEquals(RepartoPago.RETENIDA, anulacion.getEstado());
        assertNull(reembolso.getSaldoAFavor());
        verify(avisos, never()).alertaAdmin(anyString(), anyString());
    }

    @Test
    void elReembolsoTotalAnulaTodaLaComisionRetenida() {
        PagoEntity pago = pagoConRetencion(RepartoPago.ANULADO, RepartoPago.ANULADO);

        Reembolso reembolso = service.registrarReembolso(pago, null, 338_000, "No se aplicó");

        assertEquals(-30_000, lineasGuardadas(1).get(0).getMonto());
        assertNull(reembolso.getSaldoAFavor());
    }

    @Test
    void reembolsarUnaLineaYaPagadaNoAnulaSuComisionYDejaElSaldoAFavor() {
        PagoEntity pago = pagoConRetencion(RepartoPago.LIQUIDADO, RepartoPago.PENDIENTE);

        Reembolso reembolso = service.registrarReembolso(pago, "ped-s1", 238_000, "No llegó");

        verify(repartoRepository, never()).save(any());
        assertEquals(218_000, reembolso.getSaldoAFavor(), "lo que el negocio ya recibió por ese dinero");
        verify(avisos).alertaAdmin(eq("Reembolso de una línea ya pagada"), contains("218000 COP a favor de DriveMaster"));
    }

    @Test
    void reembolsarParteDeUnaLineaPagadaDejaLaParteProporcional() {
        PagoEntity pago = pagoConRetencion(RepartoPago.LIQUIDADO, RepartoPago.PENDIENTE);

        Reembolso reembolso = service.registrarReembolso(pago, "ped-s1", 119_000, "Devolución de una unidad");

        assertEquals(109_000, reembolso.getSaldoAFavor());
    }

    @Test
    void elReembolsoTotalConUnaLineaPagadaConservaSoloSuComision() {
        PagoEntity pago = pagoConRetencion(RepartoPago.ANULADO, RepartoPago.LIQUIDADO);

        Reembolso reembolso = service.registrarReembolso(pago, null, 338_000, "Error");

        assertEquals(-20_000, lineasGuardadas(1).get(0).getMonto());
        assertEquals(90_000, reembolso.getSaldoAFavor());
    }

    @Test
    void cotizacionSoloManoDeObraQuedaLista() {
        cotizacion(new ArrayList<>(List.of(CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA)
                .subtotal(100_000.0).build())), 119_000);

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 119_000), "txn-1");

        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.REPUESTOS_RECIBIDOS), any(), anyString(), any(), any());
        verify(repartoRepository).save(argThat(r -> RepartoPago.TALLER.equals(r.getBeneficiarioTipo()) && r.getMonto() == 119_000));
    }

    @Test
    void pagoDeUnaCotizacionQueYaCambioSeReembolsaSinTocarStock() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "DISPONIBLE"))), 11_900);
        orden.setEstado(EstadoOrden.CANCELADA);

        service.confirmar(pago(PagoMarketplaceService.COTIZACION, 11_900), "txn-1");

        verifyNoInteractions(movimientoService);
        verify(reembolsoRepository).save(any());
        verify(transiciones, never()).transicionar(any(), anyString(), any(), anyString(), any(), any());
    }

    // ─── Inicio del pago ─────────────────────────────────────────────────────

    @Test
    void unaAprobadaSinSaldoNoCobraYPasaAlTaller() {
        cotizacion(new ArrayList<>(), 0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarPagoCotizacion("cliente@demo.com", "o1"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(cotizacionService).pasarSinPago(orden, cotizacion);
        verifyNoInteractions(pagoService);
    }

    @Test
    void sinStockAntesDeCobrarNoSeGeneraLinkYVuelveAlTaller() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 3, 90_000, "DISPONIBLE"))), 107_100);
        Producto p1 = new Producto();
        p1.setId("p1");
        p1.setStockActual(1);
        when(productoService.obtenerPorId("p1")).thenReturn(p1);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarPagoCotizacion("cliente@demo.com", "o1"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(cotizacionService).devolverAprobadaADiagnosticada(eq(orden), eq(cotizacion), eq(Cotizacion.ANULADA), anyString());
        verifyNoInteractions(pagoService);
    }

    @Test
    void unaTiendaPausadaOArchivadaBloqueaElPagoYElTallerVuelveACotizar() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "DISPONIBLE"))), 11_900);
        when(cotizacionService.devolverAprobadaADiagnosticada(any(), any(), anyString(), anyString())).thenReturn(true);
        com.drivemaster.drivemaster.repository.TiendaRepository tiendas = mock(com.drivemaster.drivemaster.repository.TiendaRepository.class);
        service.setTiendaRepository(tiendas);
        for (String estado : new String[]{"ARCHIVADO", "APROBADO"}) {
            // APROBADO pero en pausa (activo=false) también deja de vender
            when(tiendas.findById("s1")).thenReturn(java.util.Optional.of(com.drivemaster.drivemaster.model.Tienda.builder()
                    .id("s1").nombre("Repuestos Sur").estadoAprobacion(estado).activo(false).build()));

            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> service.iniciarPagoCotizacion("cliente@demo.com", "o1"));
            assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
            assertEquals("Esta cotización tiene repuestos de una tienda que ya no está disponible. El taller te enviará una nueva.", e.getReason());
        }
        verify(cotizacionService, times(2)).devolverAprobadaADiagnosticada(eq(orden), eq(cotizacion), eq(Cotizacion.ANULADA), anyString());
        verify(avisos, times(2)).cotizacionTiendaNoDisponible(eq(cotizacion), eq("Repuestos Sur"));
        verifyNoInteractions(pagoService);
    }

    @Test
    void conLaTiendaActivaLaCotizacionSePagaNormalmente() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "ENCARGO"))), 11_900);
        com.drivemaster.drivemaster.repository.TiendaRepository tiendas = mock(com.drivemaster.drivemaster.repository.TiendaRepository.class);
        when(tiendas.findById("s1")).thenReturn(java.util.Optional.of(com.drivemaster.drivemaster.model.Tienda.builder()
                .id("s1").nombre("Repuestos Sur").estadoAprobacion("APROBADO").activo(true).build()));
        service.setTiendaRepository(tiendas);
        service.iniciarPagoCotizacion("cliente@demo.com", "o1");
        verify(pagoService).crearPagoMarketplace(PagoMarketplaceService.COTIZACION, "u1", 11_900, "o1", "c1");
    }

    @Test
    void linkDeCotizacionPorElSaldoAPagar() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "ENCARGO"))), 11_900);
        service.iniciarPagoCotizacion("cliente@demo.com", "o1");
        verify(pagoService).crearPagoMarketplace(PagoMarketplaceService.COTIZACION, "u1", 11_900, "o1", "c1");
    }

    // ─── Monto inválido ──────────────────────────────────────────────────────

    @Test
    void montoInvalidoRegistraReembolsoPorLoQueVerificoWompiYAlertaAlAdmin() {
        PagoEntity pago = pago(PagoMarketplaceService.DIAGNOSTICO, 50_000);

        service.montoInvalido(pago, 4_000_000L, "COP");

        verify(reembolsoRepository).save(argThat(r -> r.getMonto() == 40_000.0 && r.getPagoId() == 10L
                && r.getMotivo().contains("40000 COP") && r.getMotivo().contains("50000 COP")
                && r.getMotivo().contains("DRV-REF")));
        verify(avisos).alertaAdmin(eq("Pago con monto inválido"), contains("reembolso pendiente por 40000 COP"));
    }

    @Test
    void montoInvalidoDeUnPagoSinOrdenNiCotizacionTambienSeReembolsa() {
        // Pago de un encargo (flujo anterior): no tiene orden, cotización ni compra
        PagoEntity pago = new PagoEntity();
        pago.setId(11L);
        pago.setConcepto("SOLICITUD");
        pago.setSolicitudId("sol9");
        pago.setReferencia("DRV-SOL");
        pago.setMonto(30_000.0);

        service.montoInvalido(pago, 2_000_000L, "COP");

        verify(reembolsoRepository).save(argThat(r -> r.getMonto() == 20_000.0 && r.getOrdenServicioId() == null
                && r.getMotivo().contains("encargo sol9")));
        verify(avisos).alertaAdmin(eq("Pago con monto inválido"), anyString());
    }

    @Test
    void montoInvalidoSinMontoDeWompiSoloAlerta() {
        PagoEntity pago = pago(PagoMarketplaceService.COTIZACION, 119_000);

        service.montoInvalido(pago, null, "COP");

        verifyNoInteractions(reembolsoRepository);
        verify(avisos).alertaAdmin(eq("Pago con monto inválido"), contains("No se registró el reembolso"));
    }

    @Test
    void conUnPagoPendienteDeLaMismaCotizacionYMontoSeReutilizaSuLink() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "ENCARGO"))), 11_900);
        PagoEntity pendiente = new PagoEntity();
        pendiente.setId(77L);
        pendiente.setReferencia("DM-REF-1");
        pendiente.setMonto(11_900.0);
        when(pagoRepository.findFirstByOrdenServicioIdAndCotizacionIdAndUsuarioIdAndConceptoAndEstadoOrderByIdDesc(
                "o1", "c1", "u1", PagoMarketplaceService.COTIZACION, "PENDIENTE")).thenReturn(java.util.Optional.of(pendiente));

        service.iniciarPagoCotizacion("cliente@demo.com", "o1");

        verify(pagoService).reusarPagoMarketplace(pendiente);
        verify(pagoService, never()).crearPagoMarketplace(anyString(), anyString(), anyDouble(), anyString(), anyString());
    }

    @Test
    void unPagoPendienteDeOtroMontoNoSeReutiliza() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "ENCARGO"))), 11_900);
        PagoEntity pendiente = new PagoEntity();
        pendiente.setId(77L);
        pendiente.setMonto(9_000.0);
        when(pagoRepository.findFirstByOrdenServicioIdAndCotizacionIdAndUsuarioIdAndConceptoAndEstadoOrderByIdDesc(
                "o1", "c1", "u1", PagoMarketplaceService.COTIZACION, "PENDIENTE")).thenReturn(java.util.Optional.of(pendiente));

        service.iniciarPagoCotizacion("cliente@demo.com", "o1");

        verify(pagoService, never()).reusarPagoMarketplace(any());
        verify(pagoService).crearPagoMarketplace(PagoMarketplaceService.COTIZACION, "u1", 11_900, "o1", "c1");
    }

    @Test
    void diagnosticoVencidoNoGeneraLink() {
        orden.setEstado(EstadoOrden.ASIGNADA_A_TALLER);
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PENDIENTE);
        orden.setTarifaDiagnostico(50_000.0);
        orden.setFechaLimitePagoDiagnostico(LocalDateTime.now().minusMinutes(1));
        assertThrows(ResponseStatusException.class, () -> service.iniciarPagoDiagnostico("cliente@demo.com", "o1"));
        verifyNoInteractions(pagoService);
    }
}
