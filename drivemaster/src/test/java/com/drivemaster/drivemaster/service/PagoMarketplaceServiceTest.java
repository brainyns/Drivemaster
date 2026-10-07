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
                movimientoService, mock(com.drivemaster.drivemaster.repository.MovimientoInventarioRepository.class), usuarioService, transiciones, cotizacionService, avisos);

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
    void linkDeCotizacionPorElSaldoAPagar() {
        cotizacion(new ArrayList<>(List.of(repuesto("p1", "s1", 1, 10_000, "ENCARGO"))), 11_900);
        service.iniciarPagoCotizacion("cliente@demo.com", "o1");
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
