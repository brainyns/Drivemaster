package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.RecuperacionClave;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.RecuperacionClaveRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.LimiteIntentos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecuperacionClaveServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioService usuarioService;
    private UsuarioRepository usuarioRepository;
    private RecuperacionClaveRepository repo;
    private EmailsService emails;
    private RecuperacionClaveService servicio;
    // Pendientes guardados, por correo (el repositorio de mentira)
    private final Map<String, RecuperacionClave> pendientes = new HashMap<>();
    private Usuario ana;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        repo = mock(RecuperacionClaveRepository.class);
        emails = mock(EmailsService.class);
        // Executor en el mismo hilo: preparar() corre antes de que solicitar() responda
        servicio = new RecuperacionClaveService(usuarioService, usuarioRepository, repo, new CodigoVerificacion(encoder),
                encoder, emails, new LimiteIntentos(), Runnable::run);

        ana = Usuario.builder().id("u1").correo("ana@demo.com").password(encoder.encode("vieja1")).proveedor("LOCAL")
                .activo(true).bloqueado(true).intentosFallidos(5).build();
        when(usuarioService.buscarPorCorreo(anyString())).thenReturn(Optional.empty());
        when(usuarioService.buscarPorCorreo("ana@demo.com")).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(ana));
        when(repo.save(any())).thenAnswer(i -> {
            RecuperacionClave r = i.getArgument(0);
            pendientes.put(r.getCorreo(), r);
            return r;
        });
        when(repo.findFirstByCorreoOrderByCreadoDesc(anyString()))
                .thenAnswer(i -> Optional.ofNullable(pendientes.get((String) i.getArgument(0))));
        doAnswer(i -> pendientes.remove(((RecuperacionClave) i.getArgument(0)).getCorreo())).when(repo).delete(any());
    }

    private String codigoEnviado() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarEmail(eq("ana@demo.com"), anyString(), html.capture());
        Matcher m = Pattern.compile(">(\\d{6})<").matcher(html.getValue());
        assertTrue(m.find(), "el correo lleva el código");
        return m.group(1);
    }

    private static String razon(Runnable accion) {
        return assertThrows(ResponseStatusException.class, accion::run).getReason();
    }

    @Test
    void respondeLoMismoExistaONoLaCuentaYSoloEnviaALaQueExiste() {
        String existe = servicio.solicitar(" Ana@Demo.com ", "1.1.1.1");
        String noExiste = servicio.solicitar("nadie@demo.com", "1.1.1.1");

        assertEquals(RecuperacionClaveService.MENSAJE_ENVIADO, existe);
        assertEquals(existe, noExiste);
        verify(emails, times(1)).enviarEmail(anyString(), anyString(), anyString());
        assertEquals("u1", pendientes.get("ana@demo.com").getUsuarioId());
        assertNull(pendientes.get("nadie@demo.com").getUsuarioId(), "pendiente sin cuenta, con un código que no se envía");
    }

    @Test
    void alEscribirUnCodigoErradoLaRespuestaTampocoRevelaSiLaCuentaExiste() {
        servicio.solicitar("ana@demo.com", "1.1.1.1");
        servicio.solicitar("nadie@demo.com", "1.1.1.1");
        String errado = codigoEnviado().equals("000000") ? "111111" : "000000";

        assertEquals(razon(() -> servicio.confirmar("ana@demo.com", errado, "nueva12", "1.1.1.1")),
                razon(() -> servicio.confirmar("nadie@demo.com", errado, "nueva12", "1.1.1.1")));
    }

    @Test
    void lasCuentasDeGoogleNoRecibenCodigo() {
        Usuario google = Usuario.builder().id("g1").correo("g@demo.com").proveedor("GOOGLE").activo(true).build();
        Usuario vinculada = Usuario.builder().id("g2").correo("v@demo.com").password("hash").proveedor("GOOGLE").activo(true).build();
        when(usuarioService.buscarPorCorreo("g@demo.com")).thenReturn(Optional.of(google));
        when(usuarioService.buscarPorCorreo("v@demo.com")).thenReturn(Optional.of(vinculada));

        assertEquals(RecuperacionClaveService.MENSAJE_ENVIADO, servicio.solicitar("g@demo.com", "1.1.1.1"));
        assertEquals(RecuperacionClaveService.MENSAJE_ENVIADO, servicio.solicitar("v@demo.com", "1.1.1.1"));
        verifyNoInteractions(emails);
    }

    @Test
    void conElCodigoCambiaLaContrasenaCierraLasSesionesYDesbloquea() {
        servicio.solicitar("ana@demo.com", "1.1.1.1");
        String codigo = codigoEnviado();
        assertNotEquals(codigo, pendientes.get("ana@demo.com").getCodigoHash(), "solo se guarda el hash");
        Instant antes = Instant.now().minusSeconds(1);

        assertEquals(RecuperacionClaveService.MENSAJE_LISTO, servicio.confirmar("ana@demo.com", codigo, "nueva12", "1.1.1.1"));

        assertTrue(encoder.matches("nueva12", ana.getPassword()));
        assertNotNull(ana.getSesionesDesde());
        assertFalse(ana.getSesionesDesde().isBefore(antes), "los tokens anteriores dejan de servir");
        assertEquals(Boolean.FALSE, ana.getBloqueado());
        assertEquals(0, ana.getIntentosFallidos());
        verify(usuarioRepository).save(ana);
        assertTrue(pendientes.isEmpty(), "el código no se puede volver a usar");
        verify(emails).enviarEmail(eq("ana@demo.com"), eq("Tu contraseña fue cambiada - DriveMaster"), anyString());
    }

    @Test
    void unaContrasenaCortaNoGastaIntentos() {
        servicio.solicitar("ana@demo.com", "1.1.1.1");
        String codigo = codigoEnviado();

        assertTrue(razon(() -> servicio.confirmar("ana@demo.com", codigo, "12345", "1.1.1.1")).contains("al menos 6"));
        assertEquals(0, pendientes.get("ana@demo.com").getIntentos());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cincoCodigosErradosAnulanLaRecuperacion() {
        servicio.solicitar("ana@demo.com", "1.1.1.1");
        String errado = codigoEnviado().equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            assertTrue(razon(() -> servicio.confirmar("ana@demo.com", errado, "nueva12", "1.1.1.1")).startsWith("Código incorrecto"));
        }
        assertTrue(razon(() -> servicio.confirmar("ana@demo.com", errado, "nueva12", "1.1.1.1")).contains("Agotaste"));
        assertTrue(pendientes.isEmpty());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void unCodigoVencidoOSinPedirNoSirve() {
        assertEquals(RecuperacionClaveService.MENSAJE_SIN_CODIGO,
                razon(() -> servicio.confirmar("ana@demo.com", "123456", "nueva12", "1.1.1.1")));

        servicio.solicitar("ana@demo.com", "1.1.1.1");
        String codigo = codigoEnviado();
        pendientes.get("ana@demo.com").setVence(Instant.now().minusSeconds(1));
        assertEquals("El código venció. Pide uno nuevo", razon(() -> servicio.confirmar("ana@demo.com", codigo, "nueva12", "1.1.1.1")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void siBrevoFallaLaRespuestaEsLaMisma() {
        doThrow(new RuntimeException("sin conexión")).when(emails).enviarEmail(anyString(), anyString(), anyString());
        assertEquals(RecuperacionClaveService.MENSAJE_ENVIADO, servicio.solicitar("ana@demo.com", "1.1.1.1"));
    }
}
