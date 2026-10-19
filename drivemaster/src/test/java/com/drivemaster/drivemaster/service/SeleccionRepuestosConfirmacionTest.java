package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.SeleccionRepuestosRequest;
import com.drivemaster.drivemaster.dto.SeleccionRepuestosRequest.Item;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
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
 * Reglas de confirmación de compatibilidad al guardar repuestos: lo POR_CONFIRMAR
 * exige la confirmación, que se guarda con mecánico y fecha y deja un evento.
 */
class SeleccionRepuestosConfirmacionTest {

    private static final String CORREO = "taller@demo.com";

    private OrdenServicioRepository ordenRepository;
    private TallerService tallerService;
    private ProductoService productoService;
    private BuscadorRepuestosService buscador;
    private TransicionOrdenService transiciones;
    private SolicitudServicioServiceImpl service;

    private Taller taller;
    private OrdenServicio orden;
    private Producto aceite;

    @BeforeEach
    void setUp() {
        ordenRepository = mock(OrdenServicioRepository.class);
        tallerService = mock(TallerService.class);
        productoService = mock(ProductoService.class);
        buscador = mock(BuscadorRepuestosService.class);
        transiciones = mock(TransicionOrdenService.class);
        VehiculoService vehiculoService = mock(VehiculoService.class);

        service = new SolicitudServicioServiceImpl(ordenRepository, mock(TallerRepository.class), vehiculoService,
                mock(UsuarioService.class), tallerService, mock(CloudinaryService.class), transiciones,
                mock(AsignacionTallerService.class), mock(ParametrosMarketplace.class),
                mock(AvisosSolicitudServicio.class), productoService, buscador, mock(LiquidacionService.class),
                mock(CalificacionTallerService.class));

        taller = Taller.builder().id("t1").nombre("AutoMax").activo(true)
                .estadoAprobacion(EstadoAprobacion.APROBADO).build();
        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .estado(EstadoOrden.DIAGNOSTICADA).tallerId("t1").vehiculoId("v1")
                .mecanicoId("m1").mecanicoNombre("Carlos Pérez").repuestos(new ArrayList<>()).build();
        aceite = new Producto();
        aceite.setId("p1");
        aceite.setNombre("Aceite 10W-30");

        when(tallerService.obtenerPorUsuario(CORREO)).thenReturn(taller);
        when(ordenRepository.findById("o1")).thenAnswer(i -> Optional.of(orden));
        when(vehiculoService.obtenerEntidad("v1")).thenReturn(Vehiculo.builder().id("v1").marca("Chevrolet").modelo("Spark GT").build());
        when(productoService.obtenerPorId("p1")).thenReturn(aceite);
        when(buscador.ofertaParaSeleccion(eq(aceite), anyInt(), any(), any())).thenReturn(OfertaRepuestoDTO.builder()
                .tiendaId("s1").tiendaNombre("Tienda Norte").precio(45_000.0)
                .disponibilidad(OfertaRepuestoDTO.DISPONIBLE).compatibilidad("POR_CONFIRMAR")
                .seleccionable(true).requiereConfirmacion(true).build());
        when(transiciones.registrar(any(), any(), anyString(), any(Criteria.class), any(Update.class), anyList()))
                .thenAnswer(i -> orden);
    }

    private static SeleccionRepuestosRequest pedido(Boolean confirmo) {
        return new SeleccionRepuestosRequest(List.of(new Item("Aceite de motor", 1, "p1", confirmo)));
    }

    @SuppressWarnings("unchecked")
    private List<RepuestoSeleccionado> repuestosGuardados(ArgumentCaptor<Update> update) {
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        return (List<RepuestoSeleccionado>) set.get("repuestos");
    }

    @Test
    void sinConfirmarSeRechazaYNoSeGuardaNada() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.seleccionarRepuestos(CORREO, "o1", pedido(false)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.seleccionarRepuestos(CORREO, "o1", pedido(null)));
        verify(transiciones, never()).registrar(any(), any(), anyString(), any(Criteria.class), any(Update.class), anyList());
    }

    @Test
    void confirmadoSeGuardaConMecanicoFechaYEvento() {
        service.seleccionarRepuestos(CORREO, "o1", pedido(true));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> notas = ArgumentCaptor.forClass(List.class);
        verify(transiciones).registrar(any(), any(), anyString(), any(Criteria.class), update.capture(), notas.capture());

        RepuestoSeleccionado r = repuestosGuardados(update).get(0);
        assertEquals("POR_CONFIRMAR", r.getCompatibilidad());
        assertTrue(r.getCompatibilidadConfirmada());
        assertEquals("m1", r.getConfirmadoPorMecanicoId());
        assertEquals("Carlos Pérez", r.getConfirmadoPorMecanicoNombre());
        assertNotNull(r.getFechaConfirmacion());
        assertEquals(List.of("Compatibilidad confirmada por Carlos Pérez: Aceite 10W-30 (Tienda Norte)"), notas.getValue());
    }

    @Test
    void unaConfirmacionAnteriorSeConservaSinRepetirElEvento() {
        LocalDateTime antes = LocalDateTime.of(2026, 10, 1, 9, 30);
        orden.setRepuestos(new ArrayList<>(List.of(RepuestoSeleccionado.builder().productoId("p1")
                .compatibilidadConfirmada(true).confirmadoPorMecanicoId("m0")
                .confirmadoPorMecanicoNombre("Ana Ruiz").fechaConfirmacion(antes).build())));

        service.seleccionarRepuestos(CORREO, "o1", pedido(true));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> notas = ArgumentCaptor.forClass(List.class);
        verify(transiciones).registrar(any(), any(), anyString(), any(Criteria.class), update.capture(), notas.capture());

        RepuestoSeleccionado r = repuestosGuardados(update).get(0);
        assertEquals("Ana Ruiz", r.getConfirmadoPorMecanicoNombre());
        assertEquals(antes, r.getFechaConfirmacion());
        assertTrue(notas.getValue().isEmpty());
    }

    @Test
    void loBloqueadoPorElBuscadorNoSeGuarda() {
        when(buscador.ofertaParaSeleccion(eq(aceite), anyInt(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No es compatible"));

        assertThrows(ResponseStatusException.class, () -> service.seleccionarRepuestos(CORREO, "o1", pedido(true)));
        verify(transiciones, never()).registrar(any(), any(), anyString(), any(Criteria.class), any(Update.class), anyList());
    }
}
