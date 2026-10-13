package com.drivemaster.drivemaster.websocket;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La conexión al WebSocket se autoriza con un ticket de un solo uso de una cuenta activa, nunca con el JWT. */
class TicketHandshakeInterceptorTest {

    private TicketsWebSocket tickets;
    private UsuarioRepository usuarios;
    private TicketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tickets = new TicketsWebSocket();
        usuarios = mock(UsuarioRepository.class);
        interceptor = new TicketHandshakeInterceptor(tickets, usuarios);
    }

    private record Resultado(boolean aceptado, int status, Map<String, Object> atributos) {}

    private Resultado conectar(String query) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ws/notificaciones");
        req.setQueryString(query);
        MockHttpServletResponse res = new MockHttpServletResponse();
        Map<String, Object> atributos = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(new ServletServerHttpRequest(req), new ServletServerHttpResponse(res),
                null, atributos);
        return new Resultado(ok, res.getStatus(), atributos);
    }

    private void cuenta(String id, String rol, Boolean activo) {
        when(usuarios.findById(id)).thenReturn(Optional.of(Usuario.builder().id(id).rol(rol).activo(activo).build()));
    }

    @Test
    void conTicketValidoConectaConSuUsuarioYRol() {
        cuenta("u-taller", "TALLER", true);

        Resultado r = conectar("ticket=" + tickets.emitir("u-taller"));

        assertTrue(r.aceptado());
        assertEquals("u-taller", r.atributos().get(NotificationWebSocketHandler.ATTR_USUARIO));
        assertEquals("TALLER", r.atributos().get(NotificationWebSocketHandler.ATTR_ROL));
    }

    @Test
    void elMismoTicketNoSirveDosVeces() {
        cuenta("u1", "TIENDA", true);
        String ticket = tickets.emitir("u1");

        assertTrue(conectar("ticket=" + ticket).aceptado());
        Resultado segunda = conectar("ticket=" + ticket);

        assertFalse(segunda.aceptado());
        assertEquals(403, segunda.status());
    }

    @Test
    void sinTicketOConElJwtEnLaUrlSeRechaza() {
        assertFalse(conectar(null).aceptado());
        Resultado conToken = conectar("token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmFAZGVtby5jb20ifQ.firma");
        assertFalse(conToken.aceptado());
        assertEquals(403, conToken.status());
        verifyNoInteractions(usuarios);
    }

    @Test
    void unaCuentaDesactivadaDespuesDeEmitirElTicketSeRechaza() {
        cuenta("u2", "TALLER", false);

        Resultado r = conectar("ticket=" + tickets.emitir("u2"));

        assertFalse(r.aceptado());
        assertEquals(403, r.status());
        assertTrue(r.atributos().isEmpty());
    }

    @Test
    void unaCuentaQueYaNoExisteSeRechaza() {
        when(usuarios.findById("borrado")).thenReturn(Optional.empty());

        assertFalse(conectar("ticket=" + tickets.emitir("borrado")).aceptado());
    }
}
