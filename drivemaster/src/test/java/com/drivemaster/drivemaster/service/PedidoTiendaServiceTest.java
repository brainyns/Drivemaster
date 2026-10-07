package com.drivemaster.drivemaster.service;

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
        tallerService = mock(TallerService.class);
        TiendaRepository tiendaRepository = mock(TiendaRepository.class);
        OrdenServicioRepository ordenRepository = mock(OrdenServicioRepository.class);
        PagoRepository pagoRepository = mock(PagoRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);

        service = new PedidoTiendaService(pedidoRepository, tiendaRepository, ordenRepository, pagoRepository,
                repartoRepository, mongoTemplate, usuarioService, tallerService, transiciones, pagoMarketplace, avisos);

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
}
