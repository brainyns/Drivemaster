package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Recepción de envíos: "Ya lo recibí", autoconfirmación y "Reportar un problema". Las escrituras
 * condicionales de Mongo se emulan sobre pedidos en memoria (sincronizadas, como lo es cada operación).
 */
class RecepcionEnvioServiceTest {

    private static final String CORREO = "cliente@demo.com";

    private final List<PedidoTienda> pedidos = new ArrayList<>();
    private PedidoTiendaRepository pedidoRepository;
    private MongoTemplate mongoTemplate;
    private LiquidacionService liquidacion;
    private AvisosSolicitudServicio avisos;
    private RecepcionEnvioService service;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoTiendaRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        liquidacion = mock(LiquidacionService.class);
        avisos = mock(AvisosSolicitudServicio.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        when(usuarioService.obtenerPorCorreo(CORREO)).thenReturn(Usuario.builder().id("u1").correo(CORREO).build());
        when(pedidoRepository.findById(anyString())).thenAnswer(i -> pedidos.stream().filter(p -> p.getId().equals(i.getArgument(0))).findFirst());
        service = new RecepcionEnvioService(pedidoRepository, mongoTemplate, usuarioService, liquidacion, avisos);

        // Emulación de findAndModify condicional: la condición se lee de la consulta real que arma el servicio
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenAnswer(i -> aplicar(((Query) i.getArgument(0)).getQueryObject(), ((Update) i.getArgument(1)).getUpdateObject()));
    }

    /** Aplica el Update al primer pedido que cumpla la consulta; null si ninguno (como Mongo). */
    private synchronized PedidoTienda aplicar(Document q, Document u) {
        for (PedidoTienda p : pedidos) {
            if (!cumple(p, q)) continue;
            Document set = (Document) u.get("$set");
            if (set.containsKey("estado")) p.setEstado((String) set.get("estado"));
            if (set.containsKey("fechaRecibidoCliente")) p.setFechaRecibidoCliente((LocalDateTime) set.get("fechaRecibidoCliente"));
            if (set.containsKey("recepcionAutomatica")) p.setRecepcionAutomatica((Boolean) set.get("recepcionAutomatica"));
            if (set.containsKey("problemaReportado")) p.setProblemaReportado((Boolean) set.get("problemaReportado"));
            if (set.containsKey("problemaDetalle")) p.setProblemaDetalle((String) set.get("problemaDetalle"));
            if (set.containsKey("fechaProblema")) p.setFechaProblema((LocalDateTime) set.get("fechaProblema"));
            return p;
        }
        return null;
    }

    private static boolean cumple(PedidoTienda p, Document q) {
        for (String clave : q.keySet()) {
            Object cond = q.get(clave);
            Object valor = switch (clave) {
                case "id" -> p.getId();
                case "clienteId" -> p.getClienteId();
                case "estado" -> p.getEstado();
                case "origen" -> p.getOrigen();
                case "entrega" -> p.getEntrega();
                case "problemaReportado" -> p.getProblemaReportado();
                case "autoconfirmaDespuesDe" -> p.getAutoconfirmaDespuesDe();
                default -> throw new IllegalStateException("Campo no emulado: " + clave);
            };
            if (cond instanceof Document d) {
                if (d.containsKey("$ne") && java.util.Objects.equals(valor, d.get("$ne"))) return false;
                if (d.containsKey("$lt") && (valor == null || !((LocalDateTime) valor).isBefore((LocalDateTime) d.get("$lt")))) return false;
            } else if (!java.util.Objects.equals(valor, cond)) {
                return false;
            }
        }
        return true;
    }

    private PedidoTienda envio(String id, String estado) {
        PedidoTienda p = PedidoTienda.builder().id(id).origen(PedidoTienda.ORIGEN_CATALOGO).entrega(PedidoTienda.ENVIO)
                .clienteId("u1").tiendaId("s1").tiendaNombre("Tienda Norte").compraId("c1").estado(estado)
                .total(43_700.0).autoconfirmaDespuesDe(LocalDateTime.now().plusDays(5)).build();
        pedidos.add(p);
        return p;
    }

    private static ResponseStatusException conflicto(Runnable r) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, r::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode(), e.getReason());
        return e;
    }

    // ─── "Ya lo recibí" ──────────────────────────────────────────────────────

    @Test
    void elClienteConfirmaLaRecepcionYSeHabilitaLaLiquidacionDeLaTienda() {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);

        PedidoTienda r = service.confirmarRecepcion(CORREO, "p1");

        assertEquals(PedidoTienda.ENTREGADO, r.getEstado());
        assertNotNull(p.getFechaRecibidoCliente());
        assertFalse(p.getRecepcionAutomatica());
        verify(liquidacion).pedidoRecibido("p1", RecepcionEnvioService.EVENTO_CLIENTE);
    }

    @Test
    void confirmarDosVecesNoLiquidaDosVeces() {
        envio("p1", PedidoTienda.DESPACHADO);
        service.confirmarRecepcion(CORREO, "p1");
        conflicto(() -> service.confirmarRecepcion(CORREO, "p1"));
        verify(liquidacion, times(1)).pedidoRecibido(anyString(), anyString());
    }

    @Test
    void noSeConfirmaAntesDelDespacho() {
        envio("p1", PedidoTienda.PENDIENTE);
        assertTrue(conflicto(() -> service.confirmarRecepcion(CORREO, "p1")).getReason().contains("no ha despachado"));
        verifyNoInteractions(liquidacion);
    }

    @Test
    void soloElClienteDuenoConfirma() {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);
        p.setClienteId("otro");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.confirmarRecepcion(CORREO, "p1")).getStatusCode());
        assertEquals(PedidoTienda.DESPACHADO, p.getEstado());
        verifyNoInteractions(liquidacion);
    }

    @Test
    void unRetiroOUnPedidoDelTallerNoSeConfirmaConEstaVia() {
        PedidoTienda retiro = envio("p1", PedidoTienda.DESPACHADO);
        retiro.setEntrega(PedidoTienda.RETIRO);
        conflicto(() -> service.confirmarRecepcion(CORREO, "p1"));
        PedidoTienda taller = envio("p2", PedidoTienda.DESPACHADO);
        taller.setOrigen(null);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.confirmarRecepcion(CORREO, "p2")).getStatusCode());
        verifyNoInteractions(liquidacion);
    }

    // ─── "Reportar un problema" ──────────────────────────────────────────────

    @Test
    void reportarUnProblemaPausaLaLiquidacionYAlertaAlAdmin() {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);

        PedidoTienda r = service.reportarProblema(CORREO, "p1", "  No llegó nada  ");

        assertTrue(r.getProblemaReportado());
        assertEquals("No llegó nada", p.getProblemaDetalle());
        assertNotNull(p.getFechaProblema());
        assertEquals(PedidoTienda.DESPACHADO, p.getEstado(), "sigue despachado, sin confirmar");
        verify(avisos).problemaEnvio(p, "No llegó nada");
        verifyNoInteractions(liquidacion);
    }

    @Test
    void conUnProblemaAbiertoYaNoSeConfirmaNiSeAutoconfirma() {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);
        service.reportarProblema(CORREO, "p1", "Llegó roto");

        assertTrue(conflicto(() -> service.confirmarRecepcion(CORREO, "p1")).getReason().contains("problema"));
        p.setAutoconfirmaDespuesDe(LocalDateTime.now().minusDays(1)); // el plazo ya venció
        assertEquals(0, service.autoconfirmar(LocalDateTime.now()), "el problema pausa la autoconfirmación");
        assertEquals(PedidoTienda.DESPACHADO, p.getEstado());
        verifyNoInteractions(liquidacion);
        conflicto(() -> service.reportarProblema(CORREO, "p1", "otra vez"));
    }

    @Test
    void elProblemaExigeDetalleYDespacho() {
        envio("p1", PedidoTienda.DESPACHADO);
        for (String vacio : new String[]{null, "", "   "}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.reportarProblema(CORREO, "p1", vacio)).getStatusCode());
        }
        envio("p2", PedidoTienda.PENDIENTE);
        conflicto(() -> service.reportarProblema(CORREO, "p2", "no llegó"));
        verifyNoInteractions(avisos);
    }

    @Test
    void unPedidoYaConfirmadoNoSePuedeReportar() {
        envio("p1", PedidoTienda.DESPACHADO);
        service.confirmarRecepcion(CORREO, "p1");
        assertTrue(conflicto(() -> service.reportarProblema(CORREO, "p1", "mentira")).getReason().contains("Ya confirmaste"));
        verify(avisos, never()).problemaEnvio(any(), anyString());
    }

    @Test
    void elDetalleDelProblemaTieneTope() {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);
        service.reportarProblema(CORREO, "p1", "x".repeat(2000));
        assertEquals(RecepcionEnvioService.MAX_DETALLE, p.getProblemaDetalle().length());
    }

    @Test
    void confirmarYReportarALaVezSoloUnoGana() throws Exception {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        Future<Boolean> confirma = pool.submit(() -> {
            salida.await();
            try { service.confirmarRecepcion(CORREO, "p1"); return true; } catch (ResponseStatusException e) { return false; }
        });
        Future<Boolean> reporta = pool.submit(() -> {
            salida.await();
            try { service.reportarProblema(CORREO, "p1", "no llegó"); return true; } catch (ResponseStatusException e) { return false; }
        });
        salida.countDown();
        boolean c = confirma.get(), r = reporta.get();
        pool.shutdown();
        assertTrue(c ^ r, "exactamente uno gana: confirmar=" + c + " reportar=" + r);
        if (c) verify(liquidacion).pedidoRecibido("p1", RecepcionEnvioService.EVENTO_CLIENTE);
        else verifyNoInteractions(liquidacion);
        assertNotNull(p);
    }

    // ─── Autoconfirmación ────────────────────────────────────────────────────

    @Test
    void alVencerElPlazoSinRespuestaSeConfirmaSolaYSeHabilitaLaLiquidacion() {
        PedidoTienda vencido = envio("p1", PedidoTienda.DESPACHADO);
        vencido.setAutoconfirmaDespuesDe(LocalDateTime.now().minusHours(1));

        assertEquals(1, service.autoconfirmar(LocalDateTime.now()));

        assertEquals(PedidoTienda.ENTREGADO, vencido.getEstado());
        assertTrue(vencido.getRecepcionAutomatica());
        verify(liquidacion).pedidoRecibido("p1", RecepcionEnvioService.EVENTO_AUTO);
    }

    @Test
    void antesDelPlazoNoSeConfirma() {
        envio("p1", PedidoTienda.DESPACHADO); // vence en 5 días
        assertEquals(0, service.autoconfirmar(LocalDateTime.now()));
        verifyNoInteractions(liquidacion);
    }

    @Test
    void laAutoconfirmacionSoloTocaEnviosDelCatalogoDespachados() {
        PedidoTienda retiro = envio("p1", PedidoTienda.DESPACHADO);
        retiro.setEntrega(PedidoTienda.RETIRO);
        PedidoTienda pendiente = envio("p2", PedidoTienda.PENDIENTE);
        PedidoTienda taller = envio("p3", PedidoTienda.DESPACHADO);
        taller.setOrigen(null);
        PedidoTienda yaEntregado = envio("p4", PedidoTienda.ENTREGADO);
        for (PedidoTienda p : List.of(retiro, pendiente, taller, yaEntregado)) {
            p.setAutoconfirmaDespuesDe(LocalDateTime.now().minusDays(3));
        }
        assertEquals(0, service.autoconfirmar(LocalDateTime.now()));
        verifyNoInteractions(liquidacion);
    }

    @Test
    void laAutoconfirmacionNoRepiteNiConfirmaDosVecesElMismoPedido() {
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);
        p.setAutoconfirmaDespuesDe(LocalDateTime.now().minusHours(1));
        assertEquals(1, service.autoconfirmar(LocalDateTime.now()));
        assertEquals(0, service.autoconfirmar(LocalDateTime.now()));
        verify(liquidacion, times(1)).pedidoRecibido(anyString(), anyString());
    }

    @Test
    void laConsultaDeAutoconfirmacionExigeLasCondicionesDeLaRegla() {
        service.autoconfirmar(LocalDateTime.now());
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(q.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
        String filtro = q.getValue().getQueryObject().toString();
        for (String c : List.of("origen", "CATALOGO", "entrega", "ENVIO", "estado", "DESPACHADO", "problemaReportado", "$ne",
                "autoconfirmaDespuesDe", "$lt")) {
            assertTrue(filtro.contains(c), c + " en " + filtro);
        }
    }

    // ─── El aviso ────────────────────────────────────────────────────────────

    @Test
    void elProblemaAlertaAlAdminConLaCompraYElDetalle() {
        // Con el servicio real de avisos el texto debe nombrar la compra, el pedido y avisar de la pausa
        EmailsService emails = mock(EmailsService.class);
        com.drivemaster.drivemaster.repository.UsuarioRepository usuarios = mock(com.drivemaster.drivemaster.repository.UsuarioRepository.class);
        when(usuarios.findByRol("ADMIN")).thenReturn(List.of(Usuario.builder().correo("admin@demo.com").build()));
        when(usuarios.findByRol("SUPERADMIN")).thenReturn(List.of());
        AvisosSolicitudServicio real = new AvisosSolicitudServicio(emails, usuarios,
                mock(com.drivemaster.drivemaster.repository.TallerRepository.class),
                mock(com.drivemaster.drivemaster.repository.TiendaRepository.class),
                mock(com.drivemaster.drivemaster.service.NotificacionService.class), "https://front.test");
        PedidoTienda p = envio("p1", PedidoTienda.DESPACHADO);

        real.problemaEnvio(p, "Llego roto");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarEmail(eq("admin@demo.com"), contains("Problema con un envío"), html.capture());
        assertTrue(html.getValue().contains("c1") && html.getValue().contains("Llego roto") && html.getValue().contains("pausa"),
                html.getValue());
    }
}
