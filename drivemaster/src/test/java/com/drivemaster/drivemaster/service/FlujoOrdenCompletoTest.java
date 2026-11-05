package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AceptarSolicitudRequest;
import com.drivemaster.drivemaster.dto.CrearCotizacionRequest;
import com.drivemaster.drivemaster.dto.DiagnosticoRequest;
import com.drivemaster.drivemaster.dto.RespuestaCotizacionRequest;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ResolucionRepartoRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import com.drivemaster.drivemaster.service.dispersion.DispersionService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Una orden de punta a punta, con los servicios reales y la base simulada: las escrituras condicionales respetan el
 * estado leído, así que un segundo clic falla igual que en Mongo. El pago lo aplica PagoMarketplaceService, probado
 * aparte; aquí se simula su resultado: PAGADA con su pago y luego los estados del SISTEMA.
 */
class FlujoOrdenCompletoTest {

    private static final String TALLER = "taller@demo.com";
    private static final String CLIENTE = "cliente@demo.com";

    private OrdenServicio orden;
    private Cotizacion cotizacion;
    private RepartoPago lineaTaller;
    private TransicionOrdenService transiciones;
    private SolicitudServicioServiceImpl solicitudes;
    private CotizacionService cotizaciones;
    private OrdenServicioRepository ordenes;

    /** El estado que la escritura condicional exige, esté suelto o dentro de un $and. */
    @SuppressWarnings("unchecked")
    private static Object estadoExigido(Document consulta) {
        if (consulta.containsKey("estado")) return consulta.get("estado");
        for (Object parte : (List<Object>) consulta.getOrDefault("$and", List.of())) {
            Object estado = estadoExigido((Document) parte);
            if (estado != null) return estado;
        }
        return null;
    }

    private static void aplicar(Object destino, Update cambios) {
        BeanWrapperImpl bean = new BeanWrapperImpl(destino);
        Document set = (Document) cambios.getUpdateObject().get("$set");
        if (set != null) set.forEach(bean::setPropertyValue);
    }

    @BeforeEach
    void setUp() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ordenes = mock(OrdenServicioRepository.class);
        CotizacionRepository cotizacionRepo = mock(CotizacionRepository.class);
        ParametrosMarketplace parametros = mock(ParametrosMarketplace.class);
        AvisosSolicitudServicio avisos = mock(AvisosSolicitudServicio.class);
        TallerService tallerService = mock(TallerService.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        DispersionService dispersion = mock(DispersionService.class);

        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE).estado(EstadoOrden.ASIGNADA_A_TALLER)
                .tallerId("t1").usuarioId("u1").vehiculoId("v1").vehiculoDescripcion("Chevrolet Spark").eventos(new ArrayList<>()).build();
        lineaTaller = RepartoPago.builder().id(1L).pagoId(10L).ordenServicioId("o1").beneficiarioTipo(RepartoPago.TALLER)
                .monto(100_000.0).estado(RepartoPago.PENDIENTE).liquidacionPorEvento(true).build();

        when(ordenes.findById("o1")).thenAnswer(i -> Optional.of(orden));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OrdenServicio.class)))
                .thenAnswer(i -> {
                    Object exigido = estadoExigido(((Query) i.getArgument(0)).getQueryObject());
                    if (exigido != null && !exigido.equals(orden.getEstado())) return null;
                    aplicar(orden, i.getArgument(1));
                    return orden;
                });
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Cotizacion.class)))
                .thenAnswer(i -> {
                    aplicar(cotizacion, i.getArgument(1));
                    return cotizacion;
                });
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(OrdenServicio.class))).thenAnswer(i -> {
            aplicar(orden, i.getArgument(1));
            return null;
        });

        when(cotizacionRepo.save(any())).thenAnswer(i -> {
            cotizacion = i.getArgument(0);
            cotizacion.setId("c1");
            return cotizacion;
        });
        when(cotizacionRepo.findById("c1")).thenAnswer(i -> Optional.ofNullable(cotizacion));

        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.vigenciaCotizacionDias()).thenReturn(7);
        when(parametros.plazoRecepcionServicioDiasHabiles()).thenReturn(3);

        Taller taller = Taller.builder().id("t1").nombre("AutoMax").activo(true).estadoAprobacion(EstadoAprobacion.APROBADO)
                .mecanicos(List.of(Mecanico.builder().id("m1").nombre("Carlos").activo(true).build())).build();
        Usuario cliente = Usuario.builder().id("u1").nombre("Ana").correo(CLIENTE).rol("CLIENTE").build();
        when(tallerService.obtenerPorUsuario(TALLER)).thenReturn(taller);
        when(usuarios.obtenerPorCorreo(CLIENTE)).thenReturn(cliente);
        when(usuarios.obtenerPorId("u1")).thenReturn(cliente);

        when(repartos.findByOrdenServicioIdAndBeneficiarioTipo("o1", RepartoPago.TALLER)).thenAnswer(i -> List.of(lineaTaller));
        when(repartos.marcarPorLiquidar(eq(1L), any(), anyString())).thenAnswer(i -> {
            lineaTaller.setEstado(RepartoPago.POR_LIQUIDAR);
            lineaTaller.setEventoListo(i.getArgument(2));
            return 1;
        });
        LiquidacionService liquidacion = new LiquidacionService(repartos, mock(ReembolsoRepository.class), mock(PagoRepository.class),
                dispersion, avisos, mock(ResolucionRepartoRepository.class));

        transiciones = new TransicionOrdenService(mongo);
        solicitudes = new SolicitudServicioServiceImpl(ordenes, mock(TallerRepository.class), mock(VehiculoService.class), usuarios,
                tallerService, mock(CloudinaryService.class), transiciones, mock(AsignacionTallerService.class), parametros, avisos,
                mock(ProductoService.class), mock(BuscadorRepuestosService.class), liquidacion, mock(CalificacionTallerService.class),
                mock(HorarioTallerService.class), new ReglasTrabajoTaller(cotizacionRepo, mock(PedidoTiendaRepository.class)));
        cotizaciones = new CotizacionService(cotizacionRepo, ordenes, mongo, transiciones, tallerService, usuarios, mock(VehiculoService.class),
                mock(ProductoService.class), mock(BuscadorRepuestosService.class), parametros, avisos, liquidacion, mock(PredictorCotizacionService.class));
    }

    /** De la solicitud asignada hasta el vehículo entregado, por las mismas rutas que usa la pantalla. */
    private void hastaEntregada() {
        assertEquals(EstadoOrden.EN_REVISION, solicitudes.aceptar(TALLER, "o1",
                new AceptarSolicitudRequest(LocalDateTime.now().plusDays(1), "m1")).getEstado());
        assertEquals(EstadoOrden.DIAGNOSTICADA, solicitudes.registrarDiagnostico(TALLER, "o1", new DiagnosticoRequest(
                "Pastillas delanteras gastadas", List.of("Pastillas al 10 %"), "ALTA", null, List.of(), null, null)).getEstado());

        cotizaciones.emitir(TALLER, "o1", new CrearCotizacionRequest(List.of(
                new CrearCotizacionRequest.ManoObra("Cambio de pastillas", "ITEM", 100_000.0, null)), 2, null));
        assertEquals(EstadoOrden.COTIZADA, orden.getEstado());
        cotizaciones.responder(CLIENTE, "c1", new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, null));
        assertEquals(EstadoOrden.APROBADA, orden.getEstado());

        OrdenServicio pagada = transiciones.transicionar(orden, EstadoOrden.PAGADA, Actor.SISTEMA, "Pago confirmado", null,
                new Update().set("pagoCotizacionId", 10L));
        transiciones.transicionar(pagada, EstadoOrden.REPUESTOS_RECIBIDOS, Actor.SISTEMA, "Cotización solo de mano de obra", null, null);

        assertEquals(EstadoOrden.EN_INSTALACION, solicitudes.iniciarInstalacion(TALLER, "o1").getEstado());
        assertEquals(EstadoOrden.LISTA, solicitudes.marcarLista(TALLER, "o1").getEstado());
        assertEquals(EstadoOrden.ENTREGADA, solicitudes.marcarEntregada(TALLER, "o1").getEstado());
    }

    @Test
    void laOrdenLlegaAEntregadaYElClienteHabilitaElPagoDelTaller() {
        hastaEntregada();

        assertEquals(RepartoPago.PENDIENTE, lineaTaller.getEstado(), "entregar no le paga al taller");
        assertNotNull(orden.getAutoconfirmaDespuesDe());

        solicitudes.confirmarRecepcion(CLIENTE, "o1", 5, "Muy bien");

        assertEquals(RepartoPago.POR_LIQUIDAR, lineaTaller.getEstado());
        assertEquals("ORDEN_ENTREGADA", lineaTaller.getEventoListo());
        assertEquals(Boolean.TRUE, orden.getLiquidacionLiberada());
        assertEquals(Boolean.TRUE, orden.getRecepcionConfirmada());
    }

    @Test
    void siElClienteNoRespondeAlVencerElPlazoSeHabilitaElPago() {
        hastaEntregada();
        LocalDateTime vencido = orden.getAutoconfirmaDespuesDe().plusMinutes(1);
        when(ordenes.entregasPorLiberar(EstadoOrden.FLUJO_MARKETPLACE, EstadoOrden.ENTREGADA, vencido)).thenReturn(List.of(orden));

        assertEquals(1, solicitudes.autoconfirmarEntregas(vencido));

        assertEquals(RepartoPago.POR_LIQUIDAR, lineaTaller.getEstado());
        assertEquals(Boolean.TRUE, orden.getRecepcionAutomatica());
    }

    @Test
    void dosClicsEnEntregarNoAvanzanDosVecesNiTocanElPago() {
        hastaEntregada();

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> solicitudes.marcarEntregada(TALLER, "o1"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("Esta solicitud ya está en \"Entregada\".", e.getReason());
        assertEquals(RepartoPago.PENDIENTE, lineaTaller.getEstado());
    }

    @Test
    void sinPagoLaOrdenNoSeInstalaAunqueLlegueAlEstadoPorOtroCamino() {
        orden.setEstado(EstadoOrden.REPUESTOS_RECIBIDOS);
        orden.setCotizacionId("c1");
        cotizacion = Cotizacion.builder().id("c1").estado(Cotizacion.APROBADA).totalAPagar(50_000.0).items(List.of()).build();

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> solicitudes.iniciarInstalacion(TALLER, "o1"));

        assertEquals("La cotización de esta solicitud no está pagada", e.getReason());
        assertEquals(EstadoOrden.REPUESTOS_RECIBIDOS, orden.getEstado());
    }

    @Test
    void unaCotizacionSinSaldoLlegaHastaEntregadaSinPago() {
        solicitudes.aceptar(TALLER, "o1", new AceptarSolicitudRequest(LocalDateTime.now().plusDays(1), "m1"));
        // Lo que deja el pago del diagnóstico: más que la mano de obra que se cotiza después
        orden.setPagoDiagnosticoEstado(EstadoOrden.PAGO_PAGADO);
        orden.setTarifaDiagnostico(59_500.0);
        solicitudes.registrarDiagnostico(TALLER, "o1", new DiagnosticoRequest(
                "Solo un ajuste menor del freno", List.of(), "BAJA", null, List.of(), null, null));
        cotizaciones.emitir(TALLER, "o1", new CrearCotizacionRequest(List.of(
                new CrearCotizacionRequest.ManoObra("Ajuste de freno", "ITEM", 40_000.0, null)), null, null));
        assertEquals(0, cotizacion.getTotalAPagar());

        cotizaciones.responder(CLIENTE, "c1", new RespuestaCotizacionRequest(RespuestaCotizacionRequest.APROBAR, null, null));

        assertEquals(EstadoOrden.REPUESTOS_RECIBIDOS, orden.getEstado(), "sin pago de por medio");
        assertNull(orden.getPagoCotizacionId());
        solicitudes.iniciarInstalacion(TALLER, "o1");
        solicitudes.marcarLista(TALLER, "o1");
        assertEquals(EstadoOrden.ENTREGADA, solicitudes.marcarEntregada(TALLER, "o1").getEstado());
    }

    @Test
    void elClienteNoConfirmaAntesDeLaEntrega() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> solicitudes.confirmarRecepcion(CLIENTE, "o1", null, null));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(RepartoPago.PENDIENTE, lineaTaller.getEstado());
    }
}
