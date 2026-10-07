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

import java.util.ArrayList;
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
    private SolicitudServicioServiceImpl service;
    private OrdenServicio orden;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        tallerService = mock(TallerService.class);
        avisos = mock(AvisosSolicitudServicio.class);
        OrdenServicioRepository ordenRepository = mock(OrdenServicioRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        VehiculoService vehiculoService = mock(VehiculoService.class);

        service = new SolicitudServicioServiceImpl(ordenRepository, mock(TallerRepository.class), vehiculoService,
                usuarioService, tallerService, mock(CloudinaryService.class), new TransicionOrdenService(mongoTemplate),
                mock(AsignacionTallerService.class), mock(ParametrosMarketplace.class), avisos,
                mock(ProductoService.class), mock(BuscadorRepuestosService.class));

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
        assertEquals(EstadoOrden.ENTREGADA, service.marcarEntregada(CORREO_TALLER, "o1").getEstado());
    }

    @Test
    void noSeSaltanEtapas() {
        orden.setEstado(EstadoOrden.PAGADA);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.iniciarInstalacion(CORREO_TALLER, "o1"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());

        orden.setEstado(EstadoOrden.EN_INSTALACION);
        assertThrows(ResponseStatusException.class, () -> service.marcarEntregada(CORREO_TALLER, "o1"));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OrdenServicio.class));
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
    }

    @Test
    void sinCalificacionTambienSeConfirma() {
        orden.setEstado(EstadoOrden.ENTREGADA);
        service.confirmarRecepcion(CORREO_CLIENTE, "o1", null, null);
        Document set = (Document) ultimaEscritura().getUpdateObject().get("$set");
        assertEquals(true, set.get("recepcionConfirmada"));
        assertFalse(set.containsKey("calificacionTaller"));
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
