package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.UsuarioController;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.CuentaNegocioService;
import com.drivemaster.drivemaster.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Desactivar la cuenta de un taller o una tienda (botón "Desactivar" o cambio de estado) pausa su negocio.
 * Reactivar la cuenta NO reactiva el negocio: eso es una decisión manual en Aprobaciones.
 */
@WebMvcTest(controllers = UsuarioController.class)
@Import(SecurityConfig.class)
class DesactivarCuentaPausaNegocioWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private UsuarioService usuarioService;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private CuentaNegocioService cuentaNegocioService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private Usuario taller;

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findByCorreo("jefe@demo.com"))
                .thenReturn(Optional.of(Usuario.builder().id("u0").rol("SUPERADMIN").build()));
        when(usuarioService.actualizarUsuario(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        taller = Usuario.builder().id("u-taller").correo("taller@demo.com").rol("TALLER").activo(true).build();
        when(usuarioService.obtenerPorId("u-taller")).thenReturn(taller);
    }

    @Test
    void elBotonDesactivarPausaElNegocio() throws Exception {
        mvc.perform(delete("/api/usuarios/u-taller").with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk());

        assertFalse(taller.getActivo());
        verify(cuentaNegocioService).pausarNegocioDe(taller);
    }

    @Test
    void desactivarPorCambioDeEstadoTambienPausaElNegocio() throws Exception {
        mvc.perform(put("/api/usuarios/u-taller").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")
                        .with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk());

        assertFalse(taller.getActivo());
        verify(cuentaNegocioService).pausarNegocioDe(taller);
    }

    @Test
    void reactivarLaCuentaNoReactivaElNegocio() throws Exception {
        taller.setActivo(false);

        mvc.perform(put("/api/usuarios/u-taller").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}")
                        .with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk());

        assertTrue(taller.getActivo());
        verify(cuentaNegocioService, never()).pausarNegocioDe(any());
    }

    @Test
    void editarSinTocarElEstadoNoPausaNada() throws Exception {
        mvc.perform(put("/api/usuarios/u-taller").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo nombre\"}")
                        .with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk());

        verify(cuentaNegocioService, never()).pausarNegocioDe(any());
    }
}
