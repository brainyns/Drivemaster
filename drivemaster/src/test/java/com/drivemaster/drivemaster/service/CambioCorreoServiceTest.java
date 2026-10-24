package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CambioCorreo;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.CambioCorreoRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.LimiteIntentos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CambioCorreoServiceTest {

    // BCrypt de verdad, con costo bajo para que las pruebas sean rápidas
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioService usuarioService;
    private UsuarioRepository usuarioRepository;
    private CambioCorreoRepository cambios;
    private TiendaRepository tiendas;
    private EmailsService emails;
    private CambioCorreoService servicio;
    private CambioCorreo pendiente;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cambios = mock(CambioCorreoRepository.class);
        tiendas = mock(TiendaRepository.class);
        emails = mock(EmailsService.class);
        servicio = new CambioCorreoService(usuarioService, usuarioRepository, cambios, tiendas, encoder, emails,
                new LimiteIntentos());
        Usuario cuenta = Usuario.builder().id("u1").correo("tienda@demo.com").password(encoder.encode("secreta1")).rol("TIENDA").build();
        when(usuarioService.obtenerPorCorreo("tienda@demo.com")).thenReturn(cuenta);
        when(cambios.save(any())).thenAnswer(i -> {
            pendiente = i.getArgument(0);
            return pendiente;
        });
        when(cambios.findFirstByUsuarioIdOrderByCreadoDesc("u1")).thenAnswer(i -> Optional.ofNullable(pendiente));
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tiendas.findByUsuarioId("u1")).thenReturn(Optional.empty());
    }

    private static int estado(ResponseStatusException e) {
        return e.getStatusCode().value();
    }

    /** Pide el código y devuelve el que salió en el correo. */
    private String pedirCodigo() {
        servicio.solicitar("tienda@demo.com", "secreta1", " Nueva@Demo.com ");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarEmail(eq("nueva@demo.com"), anyString(), html.capture());
        Matcher m = Pattern.compile(">(\\d{6})<").matcher(html.getValue());
        assertTrue(m.find(), "el correo lleva el código");
        return m.group(1);
    }

    @Test
    void pideLaContrasenaActualAntesDeEnviarNada() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.solicitar("tienda@demo.com", "otra", "nueva@demo.com"));
        assertEquals(400, estado(e));
        assertEquals("La contraseña actual no es correcta", e.getReason());
        verifyNoInteractions(emails);
    }

    @Test
    void unCorreoYaRegistradoNoRecibeCodigo() {
        when(usuarioService.correoOcupado("ocupado@demo.com")).thenReturn(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.solicitar("tienda@demo.com", "secreta1", "Ocupado@demo.com"));
        assertEquals(409, estado(e));
        verifyNoInteractions(emails);
    }

    @Test
    void guardaSoloElHashDelCodigoYVenceEnQuinceMinutos() {
        String codigo = pedirCodigo();
        assertNotEquals(codigo, pendiente.getCodigoHash());
        assertTrue(encoder.matches(codigo, pendiente.getCodigoHash()));
        assertEquals("nueva@demo.com", pendiente.getCorreoNuevo());
        long minutos = Duration.between(Instant.now(), pendiente.getVence()).toMinutes();
        assertTrue(minutos >= 14 && minutos <= 15);
    }

    @Test
    void siBrevoFallaNoQuedaUnCodigoPendiente() {
        doThrow(new RuntimeException("sin conexión")).when(emails).enviarEmail(anyString(), anyString(), anyString());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.solicitar("tienda@demo.com", "secreta1", "nueva@demo.com"));
        assertEquals(503, estado(e));
        verify(cambios).delete(pendiente);
    }

    @Test
    void conElCodigoCorrectoCambiaElCorreoYAvisaAlAnterior() {
        Tienda tienda = Tienda.builder().id("t1").usuarioId("u1").correoContacto("tienda@demo.com").build();
        when(tiendas.findByUsuarioId("u1")).thenReturn(Optional.of(tienda));
        String codigo = pedirCodigo();

        Usuario actualizado = servicio.confirmar("tienda@demo.com", codigo);

        assertEquals("nueva@demo.com", actualizado.getCorreo());
        assertEquals("nueva@demo.com", tienda.getCorreoContacto(), "el contacto de la tienda sigue a la cuenta");
        ArgumentCaptor<String> aviso = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarEmail(eq("tienda@demo.com"), anyString(), aviso.capture());
        assertTrue(aviso.getValue().contains("El correo de tu cuenta fue cambiado. Si no fuiste tú, contacta al administrador"));
        verify(cambios).delete(pendiente);
    }

    @Test
    void cincoCodigosErradosAnulanElCambio() {
        String codigo = pedirCodigo();
        String errado = codigo.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> servicio.confirmar("tienda@demo.com", errado));
            assertTrue(e.getReason().startsWith("Código incorrecto"));
        }
        ResponseStatusException ultimo = assertThrows(ResponseStatusException.class,
                () -> servicio.confirmar("tienda@demo.com", errado));
        assertTrue(ultimo.getReason().contains("Agotaste"));
        verify(cambios).delete(pendiente);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void unCodigoVencidoNoSirve() {
        String codigo = pedirCodigo();
        pendiente.setVence(Instant.now().minusSeconds(1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.confirmar("tienda@demo.com", codigo));
        assertEquals("El código venció. Pide uno nuevo", e.getReason());
        verify(usuarioRepository, never()).save(any());
    }
}
