package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.bson.Document;
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
 * Etapas finales: instalación, pruebas, lista, entrega y confirmación del cliente.
 * Usa el TransicionOrdenService real para comprobar la tabla de transiciones.
 */
class EtapasFinalesTest {

    private static final String CORREO_TALLER = "taller@demo.com";
    private static final String CORREO_CLIENTE = "cliente@demo.com";

    private MongoTemplate mongoTemplate;
    private TallerService tallerService;
    private AvisosSolicitudServicio avisos;
    private LiquidacionService liquidacion;
    private CalificacionTallerService calificaciones;
    private SolicitudServicioServiceImpl service;
    private OrdenServicioRepository ordenRepository;
    private ParametrosMarketplace parametros;
    private OrdenServicio orden;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        tallerService = mock(TallerService.class);
        avisos = mock(AvisosSolicitudServicio.class);
        liquidacion = mock(LiquidacionService.class);
        calificaciones = mock(CalificacionTallerService.class);
        ordenRepository = mock(OrdenServicioRepository.class);
        parametros = mock(ParametrosMarketplace.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        VehiculoService vehiculoService = mock(VehiculoService.class);

        service = new SolicitudServicioServiceImpl(ordenRepository, mock(TallerRepository.class), vehiculoService,
                usuarioService, tallerService, mock(CloudinaryService.class), new TransicionOrdenService(mongoTemplate),
                mock(AsignacionTallerService.class), parametros, avisos,
                mock(ProductoService.class), mock(BuscadorRepuestosService.class), liquidacion, calificaciones,
                mock(HorarioTallerService.class));

        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE).tallerId("t1").usuarioId("u1")
                .vehiculoId("v1").mecanicoNombre("Carlos").eventos(new ArrayList<>()).build();
        when(ordenRepository.findById("o1")).thenAnswer(i -> Optional.of(orden));
        when(tallerService.obtenerPorUsuario(CORREO_TALLER)).thenReturn(Taller.builder().id("t1").nombre("AutoMax")
                .activo(true).estadoAprobacion(EstadoAprobacion.APROBADO).build());
        when(usuarioService.obtenerPorCorreo(CORREO_CLIENTE))
                .thenReturn(Usuario.builder().id("u1").nombre("Ana").rol("CLIENTE").build());
        when(vehiculoService.obtenerEntidad(any())).thenThrow(new RuntimeException("sin vehículo"));
        // La escritura condicional aplica y devuelve la orden con el estado nuevo
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OrdenServicio.class)))
                .thenAnswer(i -> {
                    Document set = (Document) ((Update) i.getArgument(1)).getUpdateObject().get("$set");
                    orden.setEstado((String) set.get("estado"));
                    return orden;
                });
    }

    private Update ultimaEscritura() {
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, atLeastOnce()).findAndModify(any(Query.class), u.capture(), any(FindAndModifyOptions.class), eq(OrdenServicio.class));
        return u.getValue();
    }

    @Test
    void recorridoDelTallerDeRecibidosAEntregada() {
        orden.setEstado(EstadoOrden.REPUESTOS_RECIBIDOS);
        assertEquals(EstadoOrden.EN_INSTALACION, service.iniciarInstalacion(CORREO_TALLER, "o1").getEstado());
        assertTrue(ultimaEscritura().modifies("eventos"));

        service.marcarEnPruebas(CORREO_TALLER, "o1");
        assertEquals(EstadoOrden.SUB_EN_PRUEBAS,
                ((Document) ultimaEscritura().getUpdateObject().get("$set")).get("subestado"));

        assertEquals(EstadoOrden.LISTA, service.marcarLista(CORREO_TALLER, "o1").getEstado());
        verify(avisos).vehiculoListo(orden);
        when(parametros.plazoRecepcionServicioDiasHabiles()).thenReturn(3);
        assertEquals(EstadoOrden.ENTREGADA, service.marcarEntregada(CORREO_TALLER, "o1").getEstado());
        Document set = (Document) ultimaEscritura().getUpdateObject().get("$set");
        assertNotNull(set.get("autoconfirmaDespuesDe"), "corre el plazo del cliente");
        verify(avisos).vehiculoEntregado(orden, 3);
        // El taller no se habilita su propio pago: espera al cliente
        verify(liquidacion, never()).ordenEntregada(anyString());
    }

    @Test
    void siElAvisoDeEntregaFallaLaEntregaIgualQuedaYAlertaAlAdmin() {
        orden.setEstado(EstadoOrden.LISTA);
        doThrow(new IllegalStateException("sin aviso")).when(avisos).vehiculoEntregado(any(), anyInt());

        assertEquals(EstadoOrden.ENTREGADA, service.marcarEntregada(CORREO_TALLER, "o1").getEstado());

        verify(avisos).alertaAdmin(eq("Paso sin completar"), contains("no se pudo avisar al cliente"));
    }

    @Test
    void laConfirmacionDelClienteHabilitaElPagoDelTaller() {
        orden.setEstado(EstadoOrden.ENTREGADA);

        service.confirmarRecepcion(CORREO_CLIENTE, "o1", null, null);

        verify(liquidacion).ordenEntregada("o1");
        ArgumentCaptor<Update> marca = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), marca.capture(), eq(OrdenServicio.class));
        assertEquals(true, ((Document) marca.getValue().getUpdateObject().get("$set")).get("liquidacionLiberada"));
    }

    @Test
    void siHabilitarElPagoFallaLaConfirmacionIgualQuedaYSinMarcaParaReintentar() {
        orden.setEstado(EstadoOrden.ENTREGADA);
        doThrow(new IllegalStateException("postgres caído")).when(liquidacion).ordenEntregada("o1");

        assertDoesNotThrow(() -> service.confirmarRecepcion(CORREO_CLIENTE, "o1", null, null));

        verify(avisos).alertaAdmin(eq("Paso sin completar"), contains("Se reintenta solo"));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(OrdenServicio.class));
    }

    @Test
    void alVencerElPlazoSeConfirmaSolaYHabilitaElPago() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 10, 9, 0);
        orden.setEstado(EstadoOrden.ENTREGADA);
        when(ordenRepository.entregasPorLiberar(EstadoOrden.FLUJO_MARKETPLACE, EstadoOrden.ENTREGADA, ahora)).thenReturn(List.of(orden));

        assertEquals(1, service.autoconfirmarEntregas(ahora));

        Document set = (Document) ultimaEscritura().getUpdateObject().get("$set");
        assertEquals(true, set.get("recepcionConfirmada"));
        assertEquals(true, set.get("recepcionAutomatica"));
        verify(liquidacion).ordenEntregada("o1");
    }

    @Test
    void unaRecepcionYaConfirmadaSoloReintentaElPago() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 10, 9, 0);
        orden.setEstado(EstadoOrden.ENTREGADA);
        orden.setRecepcionConfirmada(true);
        when(ordenRepository.entregasPorLiberar(any(), any(), any())).thenReturn(List.of(orden));

        assertEquals(1, service.autoconfirmarEntregas(ahora));

        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OrdenServicio.class));
        verify(liquidacion).ordenEntregada("o1");
    }

    @Test
    void siElClienteConfirmaAlMismoTiempoElJobNoLoPisaYUnFalloNoFrenaLasDemas() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 10, 9, 0);
        orden.setEstado(EstadoOrden.ENTREGADA);
        OrdenServicio otra = OrdenServicio.builder().id("o2").estado(EstadoOrden.ENTREGADA).recepcionConfirmada(true).build();
        when(ordenRepository.entregasPorLiberar(any(), any(), any())).thenReturn(List.of(orden, otra));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OrdenServicio.class))).thenReturn(null);
        doThrow(new IllegalStateException("postgres caído")).when(liquidacion).ordenEntregada("o2");

        assertEquals(0, assertDoesNotThrow(() -> service.autoconfirmarEntregas(ahora)));

        verify(liquidacion, never()).ordenEntregada("o1");
        verify(liquidacion).ordenEntregada("o2");
    }

    @Test
    void laConsultaSoloTomaLoConfirmadoOVencidoYSinHabilitar() throws Exception {
        String consulta = OrdenServicioRepository.class.getMethod("entregasPorLiberar", String.class, String.class, LocalDateTime.class)
                .getAnnotation(org.springframework.data.mongodb.repository.Query.class).value();
        Document d = Document.parse(consulta.replace("?0", "'F'").replace("?1", "'E'").replace("?2", "'A'"));
        assertEquals(new Document("$ne", true), d.get("liquidacionLiberada"));
        assertEquals(2, ((java.util.List<?>) d.get("$or")).size());
    }

    @Test
    void siElAvisoFallaLaOrdenIgualQuedaLista() {
        orden.setEstado(EstadoOrden.EN_INSTALACION);
        doThrow(new IllegalStateException("sin aviso")).when(avisos).vehiculoListo(any());
        doThrow(new IllegalStateException("tampoco alerta")).when(avisos).alertaAdmin(anyString(), anyString());

        assertEquals(EstadoOrden.LISTA, service.marcarLista(CORREO_TALLER, "o1").getEstado());
    }

    @Test
    void noSeSaltanEtapas() {
        orden.setEstado(EstadoOrden.PAGADA);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarInstalacion(CORREO_TALLER, "o1"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());

        orden.setEstado(EstadoOrden.EN_INSTALACION);
        assertThrows(ResponseStatusException.class, () -> service.marcarEntregada(CORREO_TALLER, "o1"));
        // Una entrega rechazada no habilita ninguna liquidación
        verifyNoInteractions(liquidacion);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OrdenServicio.class));
    }

    @Test
    void elRechazoDeUnCambioDeEstadoSeLeeEnEspanol() {
        orden.setEstado(EstadoOrden.ENTREGADA);
        ResponseStatusException repetido = assertThrows(ResponseStatusException.class, () -> service.marcarEntregada(CORREO_TALLER, "o1"));
        assertEquals("Esta solicitud ya está en \"Entregada\".", repetido.getReason());

        orden.setEstado(EstadoOrden.LISTA);
        ResponseStatusException saltado = assertThrows(ResponseStatusException.class, () -> service.iniciarInstalacion(CORREO_TALLER, "o1"));
        assertEquals(HttpStatus.CONFLICT, saltado.getStatusCode());
        assertTrue(saltado.getReason().contains("Lista para entregar") && saltado.getReason().contains("En instalación"), saltado.getReason());
        assertFalse(saltado.getReason().contains("EN_INSTALACION"));
    }

    @Test
    void soloElTallerDeLaOrden() {
        orden.setEstado(EstadoOrden.REPUESTOS_RECIBIDOS);
        when(tallerService.obtenerPorUsuario("otro@demo.com")).thenReturn(Taller.builder().id("t9")
                .activo(true).estadoAprobacion(EstadoAprobacion.APROBADO).build());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarInstalacion("otro@demo.com", "o1"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void laTablaSoloPermiteAlTallerEntregar() {
        assertTrue(EstadoOrden.permitida(EstadoOrden.LISTA, EstadoOrden.ENTREGADA, EstadoOrden.TALLER));
        assertFalse(EstadoOrden.permitida(EstadoOrden.LISTA, EstadoOrden.ENTREGADA, EstadoOrden.CLIENTE));
        assertFalse(EstadoOrden.permitida(EstadoOrden.REPUESTOS_RECIBIDOS, EstadoOrden.EN_INSTALACION, EstadoOrden.CLIENTE));
    }

    @Test
    void elClienteConfirmaLaRecepcionConCalificacionOpcional() {
        orden.setEstado(EstadoOrden.ENTREGADA);
        service.confirmarRecepcion(CORREO_CLIENTE, "o1", 5, "Muy bien");
        Document set = (Document) ultimaEscritura().getUpdateObject().get("$set");
        assertEquals(true, set.get("recepcionConfirmada"));
        assertEquals(5, set.get("calificacionTaller"));
        // registrar no cambia el estado
        assertEquals(EstadoOrden.ENTREGADA, set.get("estado"));
        // la nueva nota actualiza el promedio del taller
        verify(calificaciones).recalcular("t1");
    }

    @Test
    void sinCalificacionTambienSeConfirma() {
        orden.setEstado(EstadoOrden.ENTREGADA);
        service.confirmarRecepcion(CORREO_CLIENTE, "o1", null, null);
        Document set = (Document) ultimaEscritura().getUpdateObject().get("$set");
        assertEquals(true, set.get("recepcionConfirmada"));
        assertFalse(set.containsKey("calificacionTaller"));
        verifyNoInteractions(calificaciones);
    }

    @Test
    void siElRecalculoFallaLaConfirmacionDelClienteIgualQueda() {
        orden.setEstado(EstadoOrden.ENTREGADA);
        doThrow(new IllegalStateException("mongo caído")).when(calificaciones).recalcular(any());
        assertDoesNotThrow(() -> service.confirmarRecepcion(CORREO_CLIENTE, "o1", 4, null));
    }

    @Test
    void reglasDeLaConfirmacion() {
        orden.setEstado(EstadoOrden.LISTA);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.confirmarRecepcion(CORREO_CLIENTE, "o1", null, null)).getStatusCode());

        orden.setEstado(EstadoOrden.ENTREGADA);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.confirmarRecepcion(CORREO_CLIENTE, "o1", 6, null)).getStatusCode());

        orden.setUsuarioId("otro");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.confirmarRecepcion(CORREO_CLIENTE, "o1", 4, null)).getStatusCode());
    }
}
