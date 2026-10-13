package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.NotificacionController;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.NotificacionService;
import com.drivemaster.drivemaster.websocket.TicketsWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El ticket del WebSocket solo se entrega con un token de acceso válido de una cuenta activa
 * (nunca con un refresh token) y cada usuario solo ve y marca sus propios avisos.
 */
@WebMvcTest(controllers = NotificacionController.class)
@Import(SecurityConfig.class)
class NotificacionesWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private NotificacionService notificaciones;
    @MockBean private TicketsWebSocket tickets;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findByCorreo("ana@demo.com"))
                .thenReturn(Optional.of(Usuario.builder().id("u-ana").correo("ana@demo.com").rol("TALLER").activo(true).build()));
        when(tickets.emitir("u-ana")).thenReturn("ticket-de-ana");
        when(tickets.segundosDeVigencia()).thenReturn(30L);
    }

    private void tokenDeAcceso(String token, boolean cuentaActiva) {
        when(jwtUtil.isRefreshToken(token)).thenReturn(false);
        when(jwtUtil.extractUsername(token)).thenReturn("ana@demo.com");
        when(jwtUtil.validateToken(eq(token), any())).thenReturn(true);
        when(customUserDetailsService.loadUserByUsername("ana@demo.com")).thenReturn(
                User.withUsername("ana@demo.com").password("x").roles("TALLER").disabled(!cuentaActiva).build());
    }

    @Test
    void sinSesionNoHayTicket() throws Exception {
        mvc.perform(post("/api/notificaciones/ticket")).andExpect(status().isUnauthorized());
        verifyNoInteractions(tickets);
    }

    @Test
    void conUnTokenDeAccesoValidoSeEntregaElTicket() throws Exception {
        tokenDeAcceso("acceso-ok", true);

        mvc.perform(post("/api/notificaciones/ticket").header("Authorization", "Bearer acceso-ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("ticket-de-ana"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(30));
    }

    @Test
    void unRefreshTokenNoSirveParaPedirTicket() throws Exception {
        when(jwtUtil.isRefreshToken("refresco")).thenReturn(true);

        mvc.perform(post("/api/notificaciones/ticket").header("Authorization", "Bearer refresco"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tickets);
    }

    @Test
    void unaCuentaDesactivadaNoRecibeTicket() throws Exception {
        tokenDeAcceso("acceso-inactiva", false);

        mvc.perform(post("/api/notificaciones/ticket").header("Authorization", "Bearer acceso-inactiva"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tickets);
    }

    @Test
    void cadaUsuarioListaSoloSusAvisos() throws Exception {
        when(notificaciones.mias("u-ana", 10)).thenReturn(List.of());
        when(notificaciones.noLeidas("u-ana")).thenReturn(2L);

        mvc.perform(get("/api/notificaciones/mias").param("limite", "10").with(user("ana@demo.com").roles("TALLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(2));
        verify(notificaciones).mias("u-ana", 10);
    }

    @Test
    void marcarComoLeidoElAvisoDeOtroDa404() throws Exception {
        when(notificaciones.marcarLeida("u-ana", "n-ajena")).thenReturn(false);
        when(notificaciones.marcarLeida("u-ana", "n-propia")).thenReturn(true);

        mvc.perform(patch("/api/notificaciones/n-ajena/leida").with(user("ana@demo.com").roles("TALLER")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/notificaciones/n-propia/leida").with(user("ana@demo.com").roles("TALLER")))
                .andExpect(status().isNoContent());
    }

    @Test
    void marcarTodasUsaElUsuarioDeLaSesion() throws Exception {
        when(notificaciones.marcarTodasLeidas("u-ana")).thenReturn(5L);

        mvc.perform(patch("/api/notificaciones/mias/leidas").with(user("ana@demo.com").roles("TALLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(5));
        verify(notificaciones, never()).marcarTodasLeidas(argThat(id -> !"u-ana".equals(id)));
        verify(notificaciones, never()).marcarLeida(anyString(), anyString());
    }
}
