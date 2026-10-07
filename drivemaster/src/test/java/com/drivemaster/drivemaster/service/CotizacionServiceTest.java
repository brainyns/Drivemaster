package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CotizacionDTO;
import com.drivemaster.drivemaster.dto.CrearCotizacionRequest;
import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.RespuestaCotizacionRequest;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.Diagnostico;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.util.TokenSeguro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
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
 * Emisión, respuesta (con sesión y con el token del correo) y vencimiento de
 * cotizaciones.
 */
class CotizacionServiceTest {

    private static final String CORREO_TALLER = "taller@demo.com";
    private static final String CORREO_CLIENTE = "cliente@demo.com";

    private CotizacionRepository cotizacionRepository;
    private OrdenServicioRepository ordenRepository;
    private MongoTemplate mongoTemplate;
    private TransicionOrdenService transiciones;
    private ParametrosMarketplace parametros;
    private AvisosSolicitudServicio avisos;
    private LiquidacionService liquidacion;
    private CotizacionService service;

    private Taller taller;
    private OrdenServicio orden;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        cotizacionRepository = mock(CotizacionRepository.class);
        ordenRepository = mock(OrdenServicioRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        transiciones = mock(TransicionOrdenService.class);
        parametros = mock(ParametrosMarketplace.class);
        avisos = mock(AvisosSolicitudServicio.class);
        liquidacion = mock(LiquidacionService.class);
        TallerService tallerService = mock(TallerService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        VehiculoService vehiculoService = mock(VehiculoService.class);
        ProductoService productoService = mock(ProductoService.class);
        BuscadorRepuestosService buscador = mock(BuscadorRepuestosService.class);

        service = new CotizacionService(cotizacionRepository, ordenRepository, mongoTemplate, transiciones,
                tallerService, usuarioService, vehiculoService, productoService, buscador, parametros, avisos, liquidacion);

        taller = Taller.builder().id("t1").nombre("AutoMax").activo(true).tarifaHora(60_000.0)
                .estadoAprobacion(EstadoAprobacion.APROBADO).build();
        cliente = Usuario.builder().id("u1").nombre("Ana").correo(CORREO_CLIENTE).rol("CLIENTE").build();
        List<RepuestoSeleccionado> repuestos = new ArrayList<>();
        repuestos.add(RepuestoSeleccionado.builder().productoId("p1").productoNombre("Pastillas").descripcion("Pastillas de freno")
                .cantidad(2).compatibilidadConfirmada(false).build());
        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .estado(EstadoOrden.DIAGNOSTICADA).tallerId("t1").usuarioId("u1").vehiculoId("v1")
                .vehiculoDescripcion("Chevrolet Spark").diagnostico(new Diagnostico())
                .pagoDiagnosticoEstado(EstadoOrden.PAGO_PAGADO).tarifaDiagnostico(50_000.0)
                .repuestos(repuestos).build();

        Producto pastillas = new Producto();
        pastillas.setId("p1");
        pastillas.setNombre("Pastillas");

        when(tallerService.obtenerPorUsuario(CORREO_TALLER)).thenReturn(taller);
        when(usuarioService.obtenerPorCorreo(CORREO_CLIENTE)).thenReturn(cliente);
        when(usuarioService.obtenerPorId("u1")).thenReturn(cliente);
        when(ordenRepository.findById("o1")).thenAnswer(i -> Optional.of(orden));
        when(vehiculoService.obtenerEntidad("v1")).thenReturn(Vehiculo.builder().id("v1").build());
        when(productoService.obtenerPorId("p1")).thenReturn(pastillas);
        when(buscador.ofertaParaSeleccion(eq(pastillas), eq(2), any(), any())).thenReturn(OfertaRepuestoDTO.builder()
                .tiendaId("s1").tiendaNombre("Tienda Norte").precio(45_000.0)
                .disponibilidad(OfertaRepuestoDTO.ENCARGO).diasEntrega(3).seleccionable(true).build());
        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.vigenciaCotizacionDias()).thenReturn(7);
        when(cotizacionRepository.save(any())).thenAnswer(i -> {
            Cotizacion c = i.getArgument(0);
            if (c.getId() == null) c.setId("c2");
            return c;
        });
        when(transiciones.transicionar(any(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(i -> orden);
    }

    private static CrearCotizacionRequest pedido() {
        return new CrearCotizacionRequest(List.of(
                new CrearCotizacionRequest.ManoObra("Cambio de pastillas", "HORAS", null, 1.5)), 2, null);
    }

    // ─── Emisión ─────────────────────────────────────────────────────────────

    @Test
    void emitirCongelaPreciosCalculaTotalesYEnviaElToken() {
        when(cotizacionRepository.findFirstByOrdenIdOrderByVersionDesc("o1"))
                .thenReturn(Optional.of(Cotizacion.builder().id("c1").version(1).build()));

        CotizacionDTO dto = service.emitir(CORREO_TALLER, "o1", pedido());

        ArgumentCaptor<Cotizacion> guardada = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizacionRepository).save(guardada.capture());
        Cotizacion c = guardada.getValue();
        assertEquals(2, c.getVersion());
        assertEquals(Cotizacion.EMITIDA, c.getEstado());
        // Repuestos 2 × 45.000 = 90.000; mano de obra 1,5 h × 60.000 = 90.000
        assertEquals(90_000, c.getSubtotalRepuestos());
        assertEquals(90_000, c.getSubtotalManoObra());
        assertEquals(34_200, c.getIva());
        assertEquals(214_200, c.getTotal());
        assertEquals(50_000, c.getAnticipoDiagnostico());
        assertEquals(164_200, c.getTotalAPagar());
        assertEquals(18_000, c.getComisionInformativa());
        assertEquals(3, c.getDiasEntregaRepuestos());
        assertEquals("s1", c.getItems().get(0).getTiendaId());
        assertEquals(45_000, c.getItems().get(0).getPrecioUnitario());

        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.COTIZADA), any(), anyString(), any(), any());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(avisos).cotizacionEmitida(any(), eq(c), token.capture());
        // Se guarda solo el hash del token que viaja en el correo
        assertEquals(TokenSeguro.hash(token.getValue()), c.getTokenHash());
        assertNotEquals(token.getValue(), c.getTokenHash());
        assertEquals(2, dto.getVersion());
    }

    @Test
    void sinDiagnosticoPagadoNoHayAnticipo() {
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_NO_REQUERIDO);
        CotizacionDTO dto = service.previsualizar(CORREO_TALLER, "o1", pedido());
        assertEquals(0, dto.getAnticipoDiagnostico());
        assertEquals(dto.getTotal(), dto.getTotalAPagar());
        verify(cotizacionRepository, never()).save(any());
    }

    @Test
    void soloSeCotizaUnaOrdenDiagnosticada() {
        orden.setEstado(EstadoOrden.EN_REVISION);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.emitir(CORREO_TALLER, "o1", pedido()));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(cotizacionRepository, never()).save(any());
    }

    @Test
    void siLaTransicionFallaSeBorraLaCotizacion() {
        when(cotizacionRepository.findFirstByOrdenIdOrderByVersionDesc("o1")).thenReturn(Optional.empty());
        when(transiciones.transicionar(any(), anyString(), any(), anyString(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "cambió"));
        assertThrows(ResponseStatusException.class, () -> service.emitir(CORREO_TALLER, "o1", pedido()));
        verify(cotizacionRepository).delete(any());
        verify(avisos, never()).cotizacionEmitida(any(), any(), any());
    }

    // ─── Enlace del correo ───────────────────────────────────────────────────

    private Cotizacion emitida(String token, LocalDateTime vigencia) {
        Cotizacion c = Cotizacion.builder().id("c1").ordenId("o1").version(1).estado(Cotizacion.EMITIDA)
                .usuarioId("u1").tallerId("t1").tokenHash(TokenSeguro.hash(token)).tokenUsado(false)
                .vigenciaHasta(vigencia).items(List.of(CotizacionItem.builder().tipo(CotizacionItem.REPUESTO)
                        .productoId("p1").tiendaId("s1").cantidad(1).precioUnitario(1000.0).build()))
                .build();
        when(cotizacionRepository.findByTokenHash(TokenSeguro.hash(token))).thenReturn(Optional.of(c));
        when(cotizacionRepository.findById("c1")).thenReturn(Optional.of(c));
        orden.setEstado(EstadoOrden.COTIZADA);
        orden.setCotizacionId("c1");
        return c;
    }

    private void marcaAplicada(Cotizacion c, String estado) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Cotizacion.class)))
                .thenAnswer(i -> {
                    c.setEstado(estado);
                    c.setTokenUsado(true);
                    return c;
                });
    }

    @Test
    void tokenInvalidoDaErrorGenerico() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.consultarPublica("xyz"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals(CotizacionService.ENLACE_INVALIDO, e.getReason());

        String desconocido = TokenSeguro.generar().token();
        when(cotizacionRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        e = assertThrows(ResponseStatusException.class, () -> service.consultarPublica(desconocido));
        assertEquals(CotizacionService.ENLACE_INVALIDO, e.getReason());
    }

    @Test
    void laVistaPublicaNoExponeIdentificadores() {
        String token = TokenSeguro.generar().token();
        emitida(token, LocalDateTime.now().plusDays(3));
        CotizacionDTO dto = service.consultarPublica(token);
        assertNull(dto.getId());
        assertNull(dto.getOrdenId());
        assertNull(dto.getComisionInformativa());
        assertNull(dto.getItems().get(0).getProductoId());
        assertNull(dto.getItems().get(0).getTiendaId());
        assertTrue(dto.isPuedeResponder());
    }

    @Test
    void tokenVencidoNoPermiteResponder() {
        String token = TokenSeguro.generar().token();
        emitida(token, LocalDateTime.now().minusMinutes(1));
        assertFalse(service.consultarPublica(token).isPuedeResponder());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.responderPublica(
                new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, token)));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(transiciones, never()).transicionar(any(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void aprobarDesdeElCorreoPasaLaOrdenAAprobada() {
        String token = TokenSeguro.generar().token();
        Cotizacion c = emitida(token, LocalDateTime.now().plusDays(3));
        marcaAplicada(c, Cotizacion.APROBADA);

        CotizacionDTO dto = service.responderPublica(
                new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, token));

        assertEquals(Cotizacion.APROBADA, dto.getEstado());
        // Aprobar no libera ninguna línea del taller: eso pasa al entregar
        verifyNoInteractions(liquidacion);
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.APROBADA),
                argThat(a -> EstadoOrden.CLIENTE.equals(a.rol()) && "u1".equals(a.id())), anyString(), any(), any());
        verify(avisos).cotizacionRespondida(eq(c), eq("aprobada"), anyString());
    }

    @Test
    void elTokenSirveUnaSolaVez() {
        String token = TokenSeguro.generar().token();
        Cotizacion c = emitida(token, LocalDateTime.now().plusDays(3));
        // Otro clic ganó: la escritura condicional no encuentra la cotización sin usar
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Cotizacion.class)))
                .thenReturn(null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.responderPublica(
                new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, token)));
        assertEquals(CotizacionService.ENLACE_INVALIDO, e.getReason());
        verify(transiciones, never()).transicionar(any(), anyString(), any(), anyString(), any(), any());

        // Y una vez usado, ya no se puede responder
        c.setTokenUsado(true);
        assertFalse(service.consultarPublica(token).isPuedeResponder());
    }

    @Test
    void pedirCambiosExigeMotivoYDevuelveLaOrdenADiagnosticada() {
        String token = TokenSeguro.generar().token();
        Cotizacion c = emitida(token, LocalDateTime.now().plusDays(3));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.responderPublica(
                new RespuestaCotizacionRequest(RespuestaCotizacionRequest.CAMBIOS, "  ", token)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());

        marcaAplicada(c, Cotizacion.CAMBIOS_SOLICITADOS);
        service.responderPublica(new RespuestaCotizacionRequest(RespuestaCotizacionRequest.CAMBIOS, "Sin pastillas", token));
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.DIAGNOSTICADA), any(), contains("Sin pastillas"), any(), any());
    }

    @Test
    void rechazarCancelaLaOrdenConMotivoDeCierre() {
        Cotizacion c = emitida(TokenSeguro.generar().token(), LocalDateTime.now().plusDays(3));
        marcaAplicada(c, Cotizacion.RECHAZADA);

        service.responder(CORREO_CLIENTE, "c1", new RespuestaCotizacionRequest(RespuestaCotizacionRequest.RECHAZAR, null, null));

        ArgumentCaptor<Update> cambios = ArgumentCaptor.forClass(Update.class);
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.CANCELADA), any(), anyString(), any(), cambios.capture());
        assertTrue(cambios.getValue().modifies("motivoCierre"));
        // Rechazo tras el diagnóstico: el taller ya trabajó, su línea del diagnóstico queda habilitada
        verify(liquidacion).ordenCanceladaPorRechazoDeCotizacion(orden.getId());
    }

    @Test
    void siLaOrdenYaCambioSeDeshaceLaRespuesta() {
        String token = TokenSeguro.generar().token();
        Cotizacion c = emitida(token, LocalDateTime.now().plusDays(3));
        marcaAplicada(c, Cotizacion.APROBADA);
        orden.setEstado(EstadoOrden.CANCELADA);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.responderPublica(
                new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, token)));
        assertEquals(CotizacionService.ENLACE_INVALIDO, e.getReason());
        ArgumentCaptor<Update> deshacer = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), deshacer.capture(), eq(Cotizacion.class));
        assertEquals(Cotizacion.EMITIDA, ((org.bson.Document) deshacer.getValue().getUpdateObject().get("$set")).get("estado"));
    }

    @Test
    void elClienteSoloRespondeSusCotizaciones() {
        Cotizacion c = emitida(TokenSeguro.generar().token(), LocalDateTime.now().plusDays(3));
        c.setUsuarioId("otro");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.responder(CORREO_CLIENTE, "c1",
                new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, null)));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    // ─── Vencimiento ─────────────────────────────────────────────────────────

    @Test
    void vencerDevuelveLaOrdenADiagnosticada() {
        Cotizacion c = emitida(TokenSeguro.generar().token(), LocalDateTime.now().minusHours(1));
        when(cotizacionRepository.findByEstadoAndVigenciaHastaBefore(eq(Cotizacion.EMITIDA), any())).thenReturn(List.of(c));
        marcaAplicada(c, Cotizacion.VENCIDA);

        assertEquals(1, service.vencerCotizaciones());
        verify(transiciones).transicionar(eq(orden), eq(EstadoOrden.DIAGNOSTICADA),
                eq(TransicionOrdenService.Actor.SISTEMA), anyString(), any(), isNull());
        verify(avisos).cotizacionVencida(c);
    }

    @Test
    void noVenceSiElClienteRespondioJustoAntes() {
        Cotizacion c = emitida(TokenSeguro.generar().token(), LocalDateTime.now().minusHours(1));
        when(cotizacionRepository.findByEstadoAndVigenciaHastaBefore(eq(Cotizacion.EMITIDA), any())).thenReturn(List.of(c));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Cotizacion.class)))
                .thenReturn(null);

        assertEquals(0, service.vencerCotizaciones());
        verify(transiciones, never()).transicionar(any(), anyString(), any(), anyString(), any(), any());
    }
}
