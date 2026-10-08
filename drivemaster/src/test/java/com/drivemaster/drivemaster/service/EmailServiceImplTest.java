package com.drivemaster.drivemaster.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Envío por Brevo: el error queda claro en el log (sin clave ni contenido) y el modo desarrollo no envía. */
class EmailServiceImplTest {

    private static final String CLAVE = "xkeysib-CLAVE-SECRETA-123";
    private static final String CONTENIDO = "<p>Tu codigo de retiro es ZZTOP-9999</p>";
    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private RestTemplate rest;
    private MockRestServiceServer brevo;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        brevo = MockRestServiceServer.createServer(rest);
        logger = (Logger) LoggerFactory.getLogger(EmailServiceImpl.class);
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private EmailServiceImpl servicio(String clave, boolean devLog) {
        return new EmailServiceImpl(rest, clave, "remitente@drivemaster.co", "DriveMaster", devLog);
    }

    private String todoElLog() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    // ─── Envío correcto ──────────────────────────────────────────────────────

    @Test
    void enviaAlDestinatarioIndicadoConLaClaveEnLaCabecera() {
        brevo.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", CLAVE))
                .andExpect(jsonPath("$.to[0].email").value("steven@demo.com"))
                .andExpect(jsonPath("$.sender.email").value("remitente@drivemaster.co"))
                .andExpect(jsonPath("$.subject").value("Hola"))
                .andRespond(withSuccess("{\"messageId\":\"<1>\"}", MediaType.APPLICATION_JSON));

        servicio(CLAVE, false).enviarEmail("steven@demo.com", "Hola", CONTENIDO);

        brevo.verify();
        assertFalse(todoElLog().contains(CLAVE));
    }

    // ─── Errores: claros y sin secretos ──────────────────────────────────────

    @Test
    void unRemitenteNoVerificadoQuedaEnElLogConLaPistaYSinClaveNiContenido() {
        brevo.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"invalid_parameter\",\"message\":\"Sender is not valid\"}"));

        assertThrows(RuntimeException.class, () -> servicio(CLAVE, false).enviarEmail("steven@demo.com", "Tu comprobante", CONTENIDO));

        String log = todoElLog();
        assertTrue(log.contains("steven@demo.com") && log.contains("Tu comprobante"));
        assertTrue(log.contains("400") && log.contains("Sender is not valid"));
        assertTrue(log.contains("verificado en Brevo"), "pista del remitente: " + log);
        assertFalse(log.contains(CLAVE), "nunca la clave");
        assertFalse(log.contains("ZZTOP-9999"), "nunca el contenido del correo");
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR));
    }

    @Test
    void unaClaveInvalidaSeDiagnosticaComoTal() {
        brevo.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        assertThrows(RuntimeException.class, () -> servicio(CLAVE, false).enviarEmail("a@demo.com", "Asunto", CONTENIDO));

        String log = todoElLog();
        assertTrue(log.contains("401") && log.contains("BREVO_API_KEY"));
        assertFalse(log.contains(CLAVE));
    }

    @Test
    void sinClaveConfiguradaNoSeIntentaEnviarYElLogLoDice() {
        assertThrows(RuntimeException.class, () -> servicio("", false).enviarEmail("a@demo.com", "Asunto", CONTENIDO));

        brevo.verify(); // no hubo ninguna llamada a Brevo
        assertTrue(todoElLog().contains("falta BREVO_API_KEY"));
    }

    @Test
    void sinConexionSeDiceQueFueLaRed() {
        brevo.expect(requestTo(URL)).andRespond(request -> {
            throw new java.io.IOException("connect timed out");
        });

        assertThrows(RuntimeException.class, () -> servicio(CLAVE, false).enviarEmail("a@demo.com", "Asunto", CONTENIDO));

        assertTrue(todoElLog().contains("sin conexión con Brevo"));
    }

    // ─── Modo solo desarrollo ────────────────────────────────────────────────

    @Test
    void enModoDesarrolloNoSeEnviaNadaYSeRegistraAsuntoYDestinatario() {
        EmailServiceImpl s = servicio(CLAVE, true);

        s.enviarEmail("steven@demo.com", "Tu comprobante", CONTENIDO);
        s.enviarEmailConAdjunto("ana@demo.com", "Factura", CONTENIDO, new byte[]{1, 2}, "f.pdf");

        brevo.verify(); // sin llamadas a Brevo
        String log = todoElLog();
        assertTrue(log.contains("steven@demo.com") && log.contains("Tu comprobante"));
        assertTrue(log.contains("ana@demo.com") && log.contains("Factura"));
        assertFalse(log.contains("ZZTOP-9999"), "el contenido del correo no se registra");
    }

    @Test
    void elModoDesarrolloVieneApagadoPorDefecto() throws Exception {
        var props = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        assertEquals("${MAIL_DEV_LOG:false}", props.getProperty("mail.dev-log"));
    }

    // ─── Código de retiro: solo en modo desarrollo y solo en el log ──────────

    private AvisosSolicitudServicio avisos(boolean devLog, EmailsService correo) {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findById("u1")).thenReturn(Optional.of(Usuario.builder().id("u1").nombre("Ana").correo("ana@demo.com").build()));
        AvisosSolicitudServicio a = new AvisosSolicitudServicio(correo, usuarios, mock(TallerRepository.class),
                mock(TiendaRepository.class), "http://localhost:5173");
        ReflectionTestUtils.setField(a, "devLog", devLog);
        return a;
    }

    private List<String> logDeAvisos(Runnable accion) {
        Logger l = (Logger) LoggerFactory.getLogger(AvisosSolicitudServicio.class);
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        l.addAppender(app);
        try {
            accion.run();
        } finally {
            l.detachAppender(app);
        }
        return app.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void elCodigoDeRetiroSoloSeEscribeEnElLogEnModoDesarrollo() {
        PedidoTienda pedido = PedidoTienda.builder().id("ped-1").clienteId("u1").tiendaNombre("T").build();
        EmailsService correo = mock(EmailsService.class);

        List<String> apagado = logDeAvisos(() -> avisos(false, correo).codigoRetiroNuevo(pedido, "ABCD-1234"));
        assertTrue(apagado.stream().noneMatch(m -> m.contains("ABCD-1234")), "apagado: el código no va al log");

        List<String> encendido = logDeAvisos(() -> avisos(true, correo).codigoRetiroNuevo(pedido, "ABCD-1234"));
        assertTrue(encendido.stream().anyMatch(m -> m.contains("ped-1") && m.contains("ABCD-1234")));

        // En ambos casos el correo real sigue el camino normal (el código viaja solo en el correo)
        verify(correo, times(2)).enviarEmail(eq("ana@demo.com"), anyString(), any());
    }

    @Test
    void laCompraPagadaRegistraLosCodigosSoloEnModoDesarrollo() {
        EmailsService correo = mock(EmailsService.class);
        var compra = com.drivemaster.drivemaster.model.CompraCatalogo.builder().id("c1").clienteId("u1").build();
        var pago = new com.drivemaster.drivemaster.model.mysql.PagoEntity();
        pago.setReferencia("DRV-1");
        Map<String, String> codigos = Map.of("ped-9", "WXYZ-5678");

        List<String> encendido = logDeAvisos(() -> avisos(true, correo).compraPagada(compra, List.of(), codigos, pago));
        assertTrue(encendido.stream().anyMatch(m -> m.contains("ped-9") && m.contains("WXYZ-5678")));

        List<String> apagado = logDeAvisos(() -> avisos(false, correo).compraPagada(compra, List.of(), codigos, pago));
        assertTrue(apagado.stream().noneMatch(m -> m.contains("WXYZ-5678")));
    }
}
