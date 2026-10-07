package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PedidoTiendaServiceTest {

    private static final String CORREO_TIENDA = "tienda@demo.com";

    private PedidoTiendaRepository pedidoRepository;
    private RepartoPagoRepository repartoRepository;
    private MongoTemplate mongoTemplate;
    private TransicionOrdenService transiciones;
    private PagoMarketplaceService pagoMarketplace;
    private AvisosSolicitudServicio avisos;
    private LiquidacionService liquidacion;
    private TallerService tallerService;
    private PedidoTiendaService service;

    private PedidoTienda pedido;
    private OrdenServicio orden;
    private PagoEntity pago;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoTiendaRepository.class);
        repartoRepository = mock(RepartoPagoRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        transiciones = mock(TransicionOrdenService.class);
        pagoMarketplace = mock(PagoMarketplaceService.class);
        avisos = mock(AvisosSolicitudServicio.class);
        liquidacion = mock(LiquidacionService.class);
        tallerService = mock(TallerService.class);
        TiendaRepository tiendaRepository = mock(TiendaRepository.class);
        OrdenServicioRepository ordenRepository = mock(OrdenServicioRepository.class);
        PagoRepository pagoRepository = mock(PagoRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);

        service = new PedidoTiendaService(pedidoRepository, tiendaRepository, ordenRepository, pagoRepository,
                repartoRepository, mongoTemplate, usuarioService, tallerService, transiciones, pagoMarketplace, avisos,
                liquidacion);

        when(usuarioService.obtenerPorCorreo(CORREO_TIENDA)).thenReturn(Usuario.builder().id("ut").rol("TIENDA").build());
        when(tiendaRepository.findByUsuarioId("ut")).thenReturn(Optional.of(Tienda.builder().id("s1").nombre("Tienda Norte").build()));
        pedido = PedidoTienda.builder().id("ped1").ordenId("o1").pagoId(10L).tiendaId("s1").tiendaNombre("Tienda Norte")
                .total(107_100.0).estado(PedidoTienda.PENDIENTE).build();
        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .estado(EstadoOrden.REPUESTOS_PEDIDOS).tallerId("t1").build();
        pago = new PagoEntity();
        pago.setId(10L);
        when(ordenRepository.findById("o1")).thenReturn(Optional.of(orden));
        when(pagoRepository.findById(10L)).thenReturn(Optional.of(pago));
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedido));
    }

    private void cambioAplicado(String estado, String motivo) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenAnswer(i -> {
                    pedido.setEstado(estado);
                    pedido.setMotivoNoDespacho(motivo);
                    return pedido;
                });
    }

    @Test
    void noPuedoDespacharAnulaElRepartoYRegistraElReembolso() {
        RepartoPago reparto = RepartoPago.builder().id(1L).pedidoTiendaId("ped1").estado(RepartoPago.PENDIENTE).monto(107_100.0).build();
        when(repartoRepository.findByPedidoTiendaId("ped1")).thenReturn(List.of(reparto));
        cambioAplicado(PedidoTienda.NO_DESPACHADO, "Sin transporte");
        when(transiciones.registrar(any(), any(), anyString(), any(), any())).thenReturn(orden);

        PedidoTienda r = service.noPuedoDespachar(CORREO_TIENDA, "ped1", PedidoTienda.MOTIVO_OTRO, "Sin transporte");

        assertEquals(PedidoTienda.NO_DESPACHADO, r.getEstado());
        assertEquals(RepartoPago.ANULADO, reparto.getEstado());
        verify(repartoRepository).save(reparto);
        verify(pagoMarketplace).registrarReembolso(eq(pago), eq("ped1"), eq(107_100.0), contains("Sin transporte"));
        verify(avisos).pedidoNoDespachado(orden, pedido);
    }

    @Test
    void noPuedoDespacharExigeMotivo() {
        // Sin tipo de motivo
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.noPuedoDespachar(CORREO_TIENDA, "ped1", null, "algo"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        // "Otro motivo" exige el detalle
        e = assertThrows(ResponseStatusException.class,
                () -> service.noPuedoDespachar(CORREO_TIENDA, "ped1", PedidoTienda.MOTIVO_OTRO, " "));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(pagoMarketplace);
    }

    @Test
    void sinRepuestoNoExigeDetalle() {
        when(repartoRepository.findByPedidoTiendaId("ped1")).thenReturn(List.of());
        cambioAplicado(PedidoTienda.NO_DESPACHADO, "No tengo el repuesto");
        when(transiciones.registrar(any(), any(), anyString(), any(), any())).thenReturn(orden);

        service.noPuedoDespachar(CORREO_TIENDA, "ped1", PedidoTienda.MOTIVO_SIN_REPUESTO, null);

        verify(pagoMarketplace).registrarReembolso(eq(pago), eq("ped1"), eq(107_100.0), contains("No tengo el repuesto"));
    }

    @Test
    void unPedidoYaDespachadoNoCambia() {
        pedido.setEstado(PedidoTienda.DESPACHADO);
        // La escritura condicional (estado PENDIENTE) no encuentra el pedido
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.noPuedoDespachar(CORREO_TIENDA, "ped1", PedidoTienda.MOTIVO_OTRO, "Sin transporte"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verifyNoInteractions(pagoMarketplace, repartoRepository);
    }

    @Test
    void elTallerConfirmaLaRecepcion() {
        when(tallerService.obtenerPorUsuario("taller@demo.com")).thenReturn(Taller.builder().id("t1").nombre("AutoMax").build());

        service.repuestosRecibidos("taller@demo.com", "o1");

        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.REPUESTOS_RECIBIDOS), any(), anyString(), any(), isNull());
        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(PedidoTienda.class));
    }

    @Test
    void otroTallerNoConfirmaRecepcion() {
        when(tallerService.obtenerPorUsuario("otro@demo.com")).thenReturn(Taller.builder().id("t9").build());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.repuestosRecibidos("otro@demo.com", "o1"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    // ─── 4-B: EN_PREPARACION y liquidación al confirmar la recepción ─────────

    private static CotizacionItem item(String disponibilidad) {
        return CotizacionItem.builder().productoId("p1").productoNombre("Filtro").cantidad(1).disponibilidad(disponibilidad).build();
    }

    @Test
    void unEncargoPasaAEnPreparacion() {
        pedido.setItems(List.of(item("ENCARGO")));
        cambioAplicado(PedidoTienda.EN_PREPARACION, null);
        when(transiciones.registrar(any(), any(), anyString(), any(), any())).thenReturn(orden);

        PedidoTienda r = service.iniciarPreparacion(CORREO_TIENDA, "ped1");

        assertEquals(PedidoTienda.EN_PREPARACION, r.getEstado());
        // La escritura es condicional: solo si el pedido sigue PENDIENTE
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(q.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
        assertTrue(q.getValue().getQueryObject().toJson().contains(PedidoTienda.PENDIENTE));
    }

    @Test
    void unPedidoMixtoTambienPasaAEnPreparacion() {
        pedido.setItems(List.of(item("DISPONIBLE"), item("ENCARGO")));
        cambioAplicado(PedidoTienda.EN_PREPARACION, null);
        when(transiciones.registrar(any(), any(), anyString(), any(), any())).thenReturn(orden);
        assertEquals(PedidoTienda.EN_PREPARACION, service.iniciarPreparacion(CORREO_TIENDA, "ped1").getEstado());
    }

    @Test
    void unPedidoSoloDeStockNoPasaPorPreparacion() {
        pedido.setItems(List.of(item("DISPONIBLE")));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarPreparacion(CORREO_TIENDA, "ped1"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
    }

    @Test
    void unPedidoQueYaNoEstaPendienteNoEntraEnPreparacion() {
        pedido.setItems(List.of(item("ENCARGO")));
        // La escritura condicional no encuentra el pedido PENDIENTE
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarPreparacion(CORREO_TIENDA, "ped1"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void otraTiendaNoPuedePrepararElPedido() {
        pedido.setTiendaId("s9");
        pedido.setItems(List.of(item("ENCARGO")));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarPreparacion(CORREO_TIENDA, "ped1"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void desdeEnPreparacionTambienSePuedeDespacharONoDespachar() {
        cambioAplicado(PedidoTienda.DESPACHADO, null);
        when(transiciones.registrar(any(), any(), anyString(), any(), any())).thenReturn(orden);
        service.despachar(CORREO_TIENDA, "ped1", null);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(q.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
        String filtro = q.getValue().getQueryObject().toJson();
        assertTrue(filtro.contains(PedidoTienda.PENDIENTE) && filtro.contains(PedidoTienda.EN_PREPARACION), filtro);
    }

    @Test
    void alConfirmarLaRecepcionLaLineaDeCadaTiendaQuedaListaParaLiquidar() {
        when(tallerService.obtenerPorUsuario("taller@demo.com")).thenReturn(Taller.builder().id("t1").nombre("AutoMax").build());
        PedidoTienda sinDespacho = PedidoTienda.builder().id("ped1").ordenId("o1").estado(PedidoTienda.PENDIENTE).build();
        PedidoTienda despachado = PedidoTienda.builder().id("ped2").ordenId("o1").estado(PedidoTienda.DESPACHADO).build();
        PedidoTienda noDespachado = PedidoTienda.builder().id("ped3").ordenId("o1").estado(PedidoTienda.NO_DESPACHADO).build();
        PedidoTienda sinDespachoDespues = PedidoTienda.builder().id("ped1").ordenId("o1").estado(PedidoTienda.RECIBIDO).build();
        PedidoTienda despachadoDespues = PedidoTienda.builder().id("ped2").ordenId("o1").estado(PedidoTienda.RECIBIDO).build();
        when(pedidoRepository.findByOrdenIdOrderByFechaCreacionAsc("o1"))
                .thenReturn(List.of(sinDespacho, despachado, noDespachado))
                .thenReturn(List.of(sinDespachoDespues, despachadoDespues, noDespachado));

        service.repuestosRecibidos("taller@demo.com", "o1");

        // La tienda que no marcó el despacho también queda lista, y el evento lo deja registrado
        verify(liquidacion).pedidoRecibido(eq("ped1"), contains("no había marcado el despacho"));
        verify(liquidacion).pedidoRecibido(eq("ped2"), eq("RECEPCION_CONFIRMADA_TALLER"));
        // El pedido que la tienda no pudo despachar no se liquida
        verify(liquidacion, never()).pedidoRecibido(eq("ped3"), anyString());
    }

    @Test
    void sinConfirmacionValidaNoSeLiquidaNada() {
        when(tallerService.obtenerPorUsuario("otro@demo.com")).thenReturn(Taller.builder().id("t9").build());
        assertThrows(ResponseStatusException.class, () -> service.repuestosRecibidos("otro@demo.com", "o1"));
        verifyNoInteractions(liquidacion);
    }
}
