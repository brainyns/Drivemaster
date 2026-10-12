package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Registro de negocios sin transacción entre colecciones: si falla guardar el taller o la tienda después de crear
 * la cuenta, la cuenta se borra. Así no queda un usuario sin negocio ni el correo ocupado para volver a intentarlo.
 */
class CuentaNegocioAtomicidadTest {

    private UsuarioRepository usuarioRepository;
    private TallerRepository tallerRepository;
    private TiendaRepository tiendaRepository;
    private CuentaNegocioService cuentas;
    private SuscripcionService suscripciones;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        tallerRepository = mock(TallerRepository.class);
        tiendaRepository = mock(TiendaRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId("u-nuevo");
            return u;
        });
        suscripciones = mock(SuscripcionService.class);
        when(suscripciones.vigente(anyString(), anyString())).thenReturn(Optional.empty());
        cuentas = new CuentaNegocioService(usuarioRepository, encoder, null, tallerRepository, tiendaRepository);
    }

    private static RegistroNegocioRequest registro() {
        RegistroNegocioRequest r = new RegistroNegocioRequest();
        r.setNombreNegocio("Negocio Demo");
        r.setCiudad("Cartagena");
        r.setDireccion("Calle 30 # 17-25");
        r.setHorario("L-S 8:00-18:00");
        r.setLatitud(10.391);
        r.setLongitud(-75.479);
        r.setCorreo("negocio@demo.com");
        r.setPassword("secreto123");
        return r;
    }

    @Test
    void siFallaGuardarElNegocioSeBorraLaCuentaYSeRelanzaElError() {
        Usuario cuenta = Usuario.builder().id("u1").correo("x@demo.com").build();
        RuntimeException falla = new IllegalStateException("Mongo caído");

        RuntimeException e = assertThrows(RuntimeException.class, () -> cuentas.conCuentaNueva(cuenta, () -> { throw falla; }));

        assertSame(falla, e);
        verify(usuarioRepository).deleteById("u1");
    }

    @Test
    void siGuardarElNegocioFuncionaLaCuentaNoSeToca() {
        Usuario cuenta = Usuario.builder().id("u1").build();

        assertEquals("guardado", cuentas.conCuentaNueva(cuenta, () -> "guardado"));

        verify(usuarioRepository, never()).deleteById(anyString());
    }

    @Test
    void siTampocoSePuedeBorrarLaCuentaSeConservaElErrorOriginal() {
        Usuario cuenta = Usuario.builder().id("u1").build();
        doThrow(new IllegalStateException("tampoco")).when(usuarioRepository).deleteById("u1");

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> cuentas.conCuentaNueva(cuenta, () -> { throw new IllegalStateException("original"); }));

        assertEquals("original", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
    }

    @Test
    void registroDeTallerFallidoNoDejaCuentaHuerfana() {
        when(tallerRepository.save(any(Taller.class))).thenThrow(new IllegalStateException("no se pudo guardar"));
        TallerServiceImpl talleres = new TallerServiceImpl(tallerRepository, usuarioRepository, cuentas, suscripciones);

        assertThrows(IllegalStateException.class, () -> talleres.registrar(registro()));

        verify(usuarioRepository).save(any(Usuario.class));
        verify(usuarioRepository).deleteById("u-nuevo");
    }

    @Test
    void registroDeTiendaFallidoNoDejaCuentaHuerfana() {
        when(tiendaRepository.save(any(Tienda.class))).thenThrow(new IllegalStateException("no se pudo guardar"));
        TiendaServiceImpl tiendas = new TiendaServiceImpl(tiendaRepository, usuarioRepository, cuentas, suscripciones);

        assertThrows(IllegalStateException.class, () -> tiendas.registrar(registro()));

        verify(usuarioRepository).save(any(Usuario.class));
        verify(usuarioRepository).deleteById("u-nuevo");
    }

    @Test
    void registroDeTallerExitosoConservaLaCuentaYLaEnlaza() {
        when(tallerRepository.save(any(Taller.class))).thenAnswer(i -> i.getArgument(0));
        TallerServiceImpl talleres = new TallerServiceImpl(tallerRepository, usuarioRepository, cuentas, suscripciones);

        talleres.registrar(registro());

        verify(tallerRepository).save(argThat(t -> "u-nuevo".equals(t.getUsuarioId())));
        verify(usuarioRepository, never()).deleteById(anyString());
    }
}
