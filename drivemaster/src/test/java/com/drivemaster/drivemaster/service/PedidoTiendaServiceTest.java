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
import com.drivemaster.drivemaster.util.CodigoRetiro;
import org.bson.Document;
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
    private OrdenServicioRepository ordenRepository;
    private ParametrosMarketplace parametros;
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
        parametros = mock(ParametrosMarketplace.class);
        when(parametros.plazoAutoconfirmacionDiasHabiles()).thenReturn(5);
        tallerService = mock(TallerService.class);
        TiendaRepository tiendaRepository = mock(TiendaRepository.class);
        ordenRepository = mock(OrdenServicioRepository.class);
        PagoRepository pagoRepository = mock(PagoRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);

        service = new PedidoTiendaService(pedidoRepository, tiendaRepository, ordenRepository, pagoRepository,
                repartoRepository, mongoTemplate, usuarioService, tallerService, transiciones, pagoMarketplace, avisos,
                liquidacion, parametros);

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

    // ─── 4-A: compras directas del catálogo (retiro con código y envío) ──────

    private PedidoTienda catalogo(String entrega, String estado, String codigo) {
        PedidoTienda p = PedidoTienda.builder().id("ped1").tiendaId("s1").tiendaNombre("Tienda Norte").pagoId(10L)
                .origen(PedidoTienda.ORIGEN_CATALOGO).compraId("c1").clienteId("u1").clienteNombre("Ana")
                .entrega(entrega).estado(estado).total(47_600.0)
                .items(List.of(item("DISPONIBLE"))).build();
        if (PedidoTienda.RETIRO.equals(entrega)) {
            p.setCodigoRetiroHash(CodigoRetiro.hash("ped1", codigo));
            p.setCodigoRetiroUsado(false);
            p.setCodigoRetiroIntentos(0);
        }
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(p));
        return p;
    }

    /** Emula las escrituras condicionales de Mongo sobre el pedido en memoria. */
    private void mongoEnMemoria(PedidoTienda p) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenAnswer(i -> {
                    Document cambios = ((Update) i.getArgument(1)).getUpdateObject();
                    Document set = (Document) cambios.get("$set");
                    if (cambios.containsKey("$inc")) {
                        p.setCodigoRetiroIntentos(p.getCodigoRetiroIntentos() + 1);
                        return p;
                    }
                    if (set != null && PedidoTienda.RETIRADO.equals(set.get("estado"))) {
                        if (!PedidoTienda.LISTO_PARA_RETIRAR.equals(p.getEstado()) || Boolean.TRUE.equals(p.getCodigoRetiroUsado())) return null;
                        p.setEstado(PedidoTienda.RETIRADO);
                        p.setCodigoRetiroUsado(true);
                        return p;
                    }
                    if (set != null && PedidoTienda.LISTO_PARA_RETIRAR.equals(set.get("estado"))) {
                        if (!PedidoTienda.PENDIENTE.equals(p.getEstado()) && !PedidoTienda.EN_PREPARACION.equals(p.getEstado())) return null;
                        p.setEstado(PedidoTienda.LISTO_PARA_RETIRAR);
                        return p;
                    }
                    if (set != null && PedidoTienda.DESPACHADO.equals(set.get("estado"))) {
                        p.setEstado(PedidoTienda.DESPACHADO);
                        return p;
                    }
                    return null;
                });
        doAnswer(i -> {
            Document set = (Document) ((Update) i.getArgument(1)).getUpdateObject().get("$set");
            p.setCodigoRetiroIntentos((Integer) set.get("codigoRetiroIntentos"));
            p.setCodigoRetiroBloqueadoHasta((java.time.LocalDateTime) set.get("codigoRetiroBloqueadoHasta"));
            return null;
        }).when(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(PedidoTienda.class));
    }

    @Test
    void elRetiroPasaAListoParaRetirarYSeAvisaAlCliente() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.PENDIENTE, "ABCD-1234");
        mongoEnMemoria(p);

        PedidoTienda r = service.listoParaRetirar(CORREO_TIENDA, "ped1");

        assertEquals(PedidoTienda.LISTO_PARA_RETIRAR, r.getEstado());
        verify(avisos).pedidoListoParaRetirar(p);
        verify(ordenRepository, never()).findById(isNull());
    }

    @Test
    void unEnvioNoSeMarcaListoParaRetirarNiUnRetiroSeDespacha() {
        PedidoTienda envio = catalogo(PedidoTienda.ENVIO, PedidoTienda.PENDIENTE, null);
        mongoEnMemoria(envio);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.listoParaRetirar(CORREO_TIENDA, "ped1")).getStatusCode());

        PedidoTienda retiro = catalogo(PedidoTienda.RETIRO, PedidoTienda.PENDIENTE, "ABCD-1234");
        mongoEnMemoria(retiro);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.despachar(CORREO_TIENDA, "ped1", null)).getStatusCode());
        assertEquals(PedidoTienda.PENDIENTE, retiro.getEstado());
    }

    @Test
    void unEnvioDelCatalogoSeDespachaSinOrdenDeServicio() {
        PedidoTienda envio = catalogo(PedidoTienda.ENVIO, PedidoTienda.PENDIENTE, null);
        mongoEnMemoria(envio);
        assertEquals(PedidoTienda.DESPACHADO, service.despachar(CORREO_TIENDA, "ped1", "Guía 123").getEstado());
        verify(ordenRepository, never()).findById(isNull());
    }

    @Test
    void laTiendaValidaElCodigoYElPedidoQuedaRetiradoYLiquidable() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        mongoEnMemoria(p);

        PedidoTienda r = service.validarRetiro(CORREO_TIENDA, "ped1", "abcd 1234");

        assertEquals(PedidoTienda.RETIRADO, r.getEstado());
        assertTrue(r.getCodigoRetiroUsado());
        // La entrega confirmada por la tienda habilita su línea del reparto, con el evento registrado
        verify(liquidacion).pedidoRecibido("ped1", "RETIRO_VALIDADO_POR_TIENDA");
    }

    @Test
    void ni_el_codigo_ni_su_hash_se_escriben_nunca_en_los_logs() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        mongoEnMemoria(p);
        String hash = p.getCodigoRetiroHash();

        java.util.List<String> logs = com.drivemaster.drivemaster.testutil.LogsCapturados.de(() -> {
            assertThrows(ResponseStatusException.class, () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ZZZZ-0000")); // incorrecto
            assertThrows(ResponseStatusException.class, () -> service.validarRetiro(CORREO_TIENDA, "ped1", "basura"));
            service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234");                                                    // correcto
            assertThrows(ResponseStatusException.class, () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")); // reuso
        });

        String todo = String.join("\n", logs);
        assertFalse(todo.contains(hash), "el hash no debe aparecer en el log");
        for (String codigo : new String[]{"ABCD-1234", "ABCD1234", "ZZZZ-0000", "ZZZZ0000"}) {
            assertFalse(todo.contains(codigo), "el código (" + codigo + ") no debe aparecer en el log");
        }
    }

    @Test
    void elCodigoEsDeUnSoloUso() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        mongoEnMemoria(p);

        service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("ya fue retirado"));
        verify(liquidacion, times(1)).pedidoRecibido(anyString(), anyString());
    }

    @Test
    void unCodigoIncorrectoNoRetiraNiLiquidaYCuentaElIntento() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        mongoEnMemoria(p);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ZZZZ-0000"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals(PedidoTienda.LISTO_PARA_RETIRAR, p.getEstado());
        assertEquals(1, p.getCodigoRetiroIntentos());
        verifyNoInteractions(liquidacion);
        // Basura y vacío también cuentan como intento fallido
        assertThrows(ResponseStatusException.class, () -> service.validarRetiro(CORREO_TIENDA, "ped1", "basura"));
        assertThrows(ResponseStatusException.class, () -> service.validarRetiro(CORREO_TIENDA, "ped1", null));
        assertEquals(3, p.getCodigoRetiroIntentos());
    }

    @Test
    void conCincoCodigosErroneosElPedidoSeBloqueaAunConElCodigoBueno() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        mongoEnMemoria(p);
        for (int i = 0; i < PedidoTiendaService.MAX_INTENTOS_CODIGO - 1; i++) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ZZZZ-0000")).getStatusCode());
        }
        // El quinto error bloquea
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ZZZZ-0000")).getStatusCode());
        assertNotNull(p.getCodigoRetiroBloqueadoHasta());
        assertTrue(p.getCodigoRetiroBloqueadoHasta().isAfter(java.time.LocalDateTime.now().plusMinutes(PedidoTiendaService.BLOQUEO_CODIGO_MINUTOS - 1)));

        // Bloqueado: ni el código correcto sirve
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")).getStatusCode());
        assertEquals(PedidoTienda.LISTO_PARA_RETIRAR, p.getEstado());
        verifyNoInteractions(liquidacion);

        // Vencido el bloqueo, el código correcto vuelve a funcionar
        p.setCodigoRetiroBloqueadoHasta(java.time.LocalDateTime.now().minusMinutes(1));
        assertEquals(PedidoTienda.RETIRADO, service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234").getEstado());
    }

    @Test
    void elCodigoDeOtroPedidoNoSirve() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        // Mismo código pero guardado para otro pedido: el hash está ligado al pedido
        p.setCodigoRetiroHash(CodigoRetiro.hash("otro-pedido", "ABCD-1234"));
        mongoEnMemoria(p);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")).getStatusCode());
    }

    @Test
    void soloLaTiendaDuenaDelPedidoValida() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        p.setTiendaId("s9"); // el pedido es de otra tienda
        mongoEnMemoria(p);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")).getStatusCode());
        assertEquals(PedidoTienda.LISTO_PARA_RETIRAR, p.getEstado());
        verifyNoInteractions(liquidacion);
    }

    @Test
    void unPedidoDelTallerNoSeValidaConCodigo() {
        // pedido de la cotización (origen TALLER): no es una compra del catálogo
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")).getStatusCode());
    }

    @Test
    void sinEstarListoOSiEsEnvioNoSeValida() {
        PedidoTienda pendiente = catalogo(PedidoTienda.RETIRO, PedidoTienda.PENDIENTE, "ABCD-1234");
        mongoEnMemoria(pendiente);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")).getStatusCode());

        PedidoTienda envio = catalogo(PedidoTienda.ENVIO, PedidoTienda.DESPACHADO, null);
        mongoEnMemoria(envio);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234")).getStatusCode());
        verifyNoInteractions(liquidacion);
    }

    @Test
    void siElClienteGeneraUnCodigoNuevoMientrasTantoElViejoYaNoRetira() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        // La escritura condicional exige el hash vigente: otro código nuevo hizo que no encuentre el pedido
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ABCD-1234"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(PedidoTienda.LISTO_PARA_RETIRAR, p.getEstado());
        verifyNoInteractions(liquidacion);
        // Y la consulta exige hash vigente y código sin usar
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(q.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
        String filtro = q.getValue().getQueryObject().toString();
        assertTrue(filtro.contains("codigoRetiroHash") && filtro.contains("codigoRetiroUsado") && filtro.contains("tiendaId"), filtro);
    }

    @Test
    void siLaTiendaNoPuedeDespacharUnaCompraDelCatalogoSeReembolsaSuParteSinOrdenDeServicio() {
        PedidoTienda p = catalogo(PedidoTienda.ENVIO, PedidoTienda.PENDIENTE, null);
        RepartoPago reparto = RepartoPago.builder().id(1L).pedidoTiendaId("ped1").estado(RepartoPago.PENDIENTE).monto(47_600.0).build();
        when(repartoRepository.findByPedidoTiendaId("ped1")).thenReturn(List.of(reparto));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenAnswer(i -> {
                    p.setEstado(PedidoTienda.NO_DESPACHADO);
                    p.setMotivoNoDespacho("Sin transporte");
                    return p;
                });

        service.noPuedoDespachar(CORREO_TIENDA, "ped1", PedidoTienda.MOTIVO_OTRO, "Sin transporte");

        assertEquals(RepartoPago.ANULADO, reparto.getEstado());
        verify(pagoMarketplace).registrarReembolso(eq(pago), eq("ped1"), eq(47_600.0), contains("Sin transporte"));
        verify(avisos).pedidoCatalogoNoDespachado(p);
        verify(avisos, never()).pedidoNoDespachado(any(), any());
        verify(ordenRepository, never()).findById(isNull());
    }

    // ─── 4-A: confirmación de recepción de envíos y problemas ────────────────

    @Test
    void alDespacharUnEnvioDelCatalogoSeProgramaLaAutoconfirmacionYSeAvisaAlCliente() {
        PedidoTienda envio = catalogo(PedidoTienda.ENVIO, PedidoTienda.PENDIENTE, null);
        ArgumentCaptor<Update> cambios = ArgumentCaptor.forClass(Update.class);
        when(mongoTemplate.findAndModify(any(Query.class), cambios.capture(), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(envio);

        service.despachar(CORREO_TIENDA, "ped1", "Guía 123");

        Document set = (Document) cambios.getValue().getUpdateObject().get("$set");
        java.time.LocalDateTime limite = (java.time.LocalDateTime) set.get("autoconfirmaDespuesDe");
        assertNotNull(limite);
        // 5 días hábiles: al menos 5 días corridos
        assertTrue(limite.isAfter(java.time.LocalDateTime.now().plusDays(5).minusMinutes(1)));
        verify(avisos).pedidoEnviado(envio, 5);
    }

    @Test
    void despacharUnPedidoDelTallerNoProgramaAutoconfirmacion() {
        // pedido de cotización (origen TALLER): lo confirma el taller, no el cliente
        ArgumentCaptor<Update> cambios = ArgumentCaptor.forClass(Update.class);
        when(mongoTemplate.findAndModify(any(Query.class), cambios.capture(), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(pedido);
        when(transiciones.registrar(any(), any(), anyString(), any(), any())).thenReturn(orden);

        service.despachar(CORREO_TIENDA, "ped1", null);

        Document set = (Document) cambios.getValue().getUpdateObject().get("$set");
        assertFalse(set.containsKey("autoconfirmaDespuesDe"));
        verify(avisos, never()).pedidoEnviado(any(), anyInt());
    }

    private PedidoTienda conProblema() {
        PedidoTienda p = catalogo(PedidoTienda.ENVIO, PedidoTienda.DESPACHADO, null);
        p.setProblemaReportado(true);
        p.setProblemaDetalle("No llegó");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenAnswer(i -> {
                    Document set = (Document) ((Update) i.getArgument(1)).getUpdateObject().get("$set");
                    if (p.getResolucionProblema() != null) return null; // otro ADMIN ya lo resolvió
                    p.setEstado((String) set.get("estado"));
                    p.setResolucionProblema((String) set.get("resolucionProblema"));
                    return p;
                });
        return p;
    }

    @Test
    void elAdminConfirmaLaEntregaYSeHabilitaLaLiquidacionDeLaTienda() {
        PedidoTienda p = conProblema();

        PedidoTienda r = service.resolverProblema("admin@demo.com", "ped1", "CONFIRMAR_ENTREGA", "La guía muestra entrega");

        assertEquals(PedidoTienda.ENTREGADO, r.getEstado());
        assertEquals("ENTREGA_CONFIRMADA_POR_ADMIN", p.getResolucionProblema());
        verify(liquidacion).pedidoRecibido("ped1", "ENTREGA_CONFIRMADA_POR_ADMIN");
        verify(avisos).problemaEnvioResuelto(p, false);
        verify(pagoMarketplace, never()).registrarReembolso(any(), any(), anyDouble(), anyString());
    }

    @Test
    void elAdminReembolsaYSeAnulaLaLineaDeLaTiendaSinLiquidar() {
        PedidoTienda p = conProblema();
        RepartoPago linea = RepartoPago.builder().id(1L).pedidoTiendaId("ped1").estado(RepartoPago.PENDIENTE).monto(47_600.0).build();
        when(repartoRepository.findByPedidoTiendaId("ped1")).thenReturn(List.of(linea));

        PedidoTienda r = service.resolverProblema("admin@demo.com", "ped1", "reembolsar", "La transportadora perdió el paquete");

        assertEquals(PedidoTienda.NO_ENTREGADO, r.getEstado());
        assertEquals(RepartoPago.ANULADO, linea.getEstado());
        verify(pagoMarketplace).registrarReembolso(eq(pago), eq("ped1"), eq(47_600.0), contains("no llegó"));
        verify(liquidacion, never()).pedidoRecibido(anyString(), anyString());
        verify(avisos).problemaEnvioResuelto(p, true);
    }

    @Test
    void soloSeResuelveUnProblemaAbiertoYUnaVez() {
        // Sin problema reportado
        PedidoTienda sinProblema = catalogo(PedidoTienda.ENVIO, PedidoTienda.DESPACHADO, null);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.resolverProblema("admin@demo.com", "ped1", "REEMBOLSAR", "Motivo")).getStatusCode());
        assertEquals(PedidoTienda.DESPACHADO, sinProblema.getEstado());

        // Con problema: el primero gana, el segundo recibe 409 y no se duplica nada
        conProblema();
        service.resolverProblema("admin@demo.com", "ped1", "CONFIRMAR_ENTREGA", "Motivo");
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(PedidoTienda.builder().id("ped1")
                .origen(PedidoTienda.ORIGEN_CATALOGO).estado(PedidoTienda.DESPACHADO).problemaReportado(true)
                .resolucionProblema("ENTREGA_CONFIRMADA_POR_ADMIN").build()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.resolverProblema("otro@demo.com", "ped1", "CONFIRMAR_ENTREGA", "Motivo")).getStatusCode());
        verify(liquidacion, times(1)).pedidoRecibido(anyString(), anyString());
    }

    @Test
    void laResolucionDebeSerUnaDeLasDosOpciones() {
        conProblema();
        for (String mala : new String[]{null, "", "ARCHIVAR"}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.resolverProblema("admin@demo.com", "ped1", mala, "Motivo")).getStatusCode());
        }
        verifyNoInteractions(liquidacion);
    }

    @Test
    void unPedidoDelTallerNoTieneProblemaDeEnvioQueResolver() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.resolverProblema("admin@demo.com", "ped1", "REEMBOLSAR", "Motivo")).getStatusCode());
    }

    // ─── Bloque 3: motivo obligatorio, intentos restantes y vista de la tienda ──

    @Test
    void resolverUnProblemaExigeElMotivoEscrito() {
        PedidoTienda p = conProblema();
        for (String vacio : new String[]{null, "", "   "}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.resolverProblema("admin@demo.com", "ped1", "REEMBOLSAR", vacio)).getStatusCode());
        }
        // Sin motivo no pasa nada: ni cambia el pedido, ni se libera el pago, ni se reembolsa
        assertEquals(PedidoTienda.DESPACHADO, p.getEstado());
        verifyNoInteractions(liquidacion);
        verify(pagoMarketplace, never()).registrarReembolso(any(), any(), anyDouble(), anyString());
    }

    @Test
    void elMotivoYQuienResolvioQuedanRegistrados() {
        PedidoTienda p = conProblema();
        ArgumentCaptor<Update> cambios = ArgumentCaptor.forClass(Update.class);
        when(mongoTemplate.findAndModify(any(Query.class), cambios.capture(), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(p);
        service.resolverProblema("admin@demo.com", "ped1", "CONFIRMAR_ENTREGA", "  El cliente la recibió  ");
        Document set = (Document) cambios.getValue().getUpdateObject().get("$set");
        assertEquals("El cliente la recibió", set.get("motivoResolucion"));
        assertEquals("admin@demo.com", set.get("resueltoPor"));
    }

    @Test
    void elReembolsoLlevaElMotivoDelAdmin() {
        conProblema();
        RepartoPago linea = RepartoPago.builder().id(1L).pedidoTiendaId("ped1").estado(RepartoPago.PENDIENTE).monto(47_600.0).build();
        when(repartoRepository.findByPedidoTiendaId("ped1")).thenReturn(List.of(linea));
        service.resolverProblema("admin@demo.com", "ped1", "REEMBOLSAR", "Paquete perdido");
        verify(pagoMarketplace).registrarReembolso(eq(pago), eq("ped1"), eq(47_600.0), contains("Paquete perdido"));
        assertTrue(linea.getNota().contains("Paquete perdido"));
    }

    @Test
    void elListadoDeProblemasLoDaElRepositorio() {
        PedidoTienda abierto = PedidoTienda.builder().id("a").problemaReportado(true).build();
        when(pedidoRepository.findByProblemaReportadoTrueOrderByFechaProblemaDesc()).thenReturn(List.of(abierto));
        assertEquals(List.of(abierto), service.problemasDeEnvio());
    }

    @Test
    void elCodigoIncorrectoDiceCuantosIntentosQuedan() {
        PedidoTienda p = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        mongoEnMemoria(p);
        String[] esperado = {"Te quedan 4 intentos", "Te quedan 3 intentos", "Te quedan 2 intentos", "Te quedan 1 intento "};
        for (String texto : esperado) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ZZZZ-0000"));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertTrue(e.getReason().contains(texto.trim()), e.getReason());
            assertTrue(e.getReason().contains("15 minutos"), e.getReason());
        }
        // El quinto error bloquea y lo dice
        ResponseStatusException bloqueo = assertThrows(ResponseStatusException.class,
                () -> service.validarRetiro(CORREO_TIENDA, "ped1", "ZZZZ-0000"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, bloqueo.getStatusCode());
        assertTrue(bloqueo.getReason().contains("se bloquea 15 minutos"), bloqueo.getReason());
    }

    @Test
    void laTiendaNoVeLosIdentificadoresNiElNombreCompletoDelClienteDeUnRetiro() {
        PedidoTienda retiro = catalogo(PedidoTienda.RETIRO, PedidoTienda.LISTO_PARA_RETIRAR, "ABCD-1234");
        retiro.setClienteNombre("Laura Gómez Pérez");

        PedidoTienda vista = PedidoTiendaService.paraTienda(retiro);

        assertNull(vista.getClienteId());
        // Solo el nombre y la inicial del apellido, para reconocer a quien llega a retirar
        assertEquals("Laura G.", vista.getClienteNombre());
        assertNull(vista.getCompraId());
        assertNull(vista.getPagoId());
        assertNull(vista.getEnvioDireccion());
        assertNull(vista.getEnvioTelefono());
        assertNull(vista.getEnvioReceptor());
        // Lo que sí necesita para despachar
        assertEquals("ped1", vista.getId());
        assertEquals(47_600.0, vista.getTotal());
        assertEquals(1, vista.getItems().size());
        assertEquals(PedidoTienda.RETIRO, vista.getEntrega());
        // El original no se toca
        assertEquals("u1", retiro.getClienteId());
    }

    @Test
    void elNombreDelRetiroQuedaEnNombreEInicial() {
        assertEquals("Ana", PedidoTiendaService.nombreEInicial(" Ana "));
        assertEquals("Laura G.", PedidoTiendaService.nombreEInicial("Laura gómez"));
        assertEquals("Laura G.", PedidoTiendaService.nombreEInicial("Laura María Gómez Pérez"));
        assertNull(PedidoTiendaService.nombreEInicial("  "));
        assertNull(PedidoTiendaService.nombreEInicial(null));
    }

    @Test
    void laDireccionSoloLlegaALaTiendaSiElPedidoEsConEnvio() {
        PedidoTienda envio = catalogo(PedidoTienda.ENVIO, PedidoTienda.PENDIENTE, null);
        envio.setEnvioReceptor("Ana Pérez");
        envio.setEnvioTelefono("3001234567");
        envio.setEnvioDireccion("Cra 10 # 20-30");
        envio.setEnvioCiudad("Medellín");
        envio.setEnvioIndicaciones("Apto 301");

        PedidoTienda vista = PedidoTiendaService.paraTienda(envio);

        assertEquals("Ana Pérez", vista.getEnvioReceptor());
        assertEquals("3001234567", vista.getEnvioTelefono());
        assertEquals("Cra 10 # 20-30", vista.getEnvioDireccion());
        assertEquals("Medellín", vista.getEnvioCiudad());
        assertNull(vista.getClienteId());
        // En un envío basta quien recibe: el nombre de la cuenta no llega
        assertNull(vista.getClienteNombre());
        assertNull(vista.getPagoId());

        // Aunque por error un retiro trajera dirección, la vista la quita
        PedidoTienda retiroConDireccion = envio.toBuilder().entrega(PedidoTienda.RETIRO).build();
        assertNull(PedidoTiendaService.paraTienda(retiroConDireccion).getEnvioDireccion());
        assertNull(PedidoTiendaService.paraTienda(retiroConDireccion).getEnvioTelefono());
    }

    @Test
    void laTiendaSoloConsultaSusPropiosPedidos() {
        when(pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc("s1")).thenReturn(List.of(pedido));
        assertEquals(List.of(pedido), service.misPedidos(CORREO_TIENDA));
        // Siempre por el id de SU tienda (sacado de su sesión), nunca por un parámetro de la petición
        verify(pedidoRepository).findByTiendaIdOrderByFechaCreacionDesc("s1");
        verify(pedidoRepository, never()).findAll();
    }
}
