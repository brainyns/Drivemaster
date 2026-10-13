package com.drivemaster.drivemaster.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Cada aviso llega solo a las pestañas de su destinatario (o de los roles indicados). */
class NotificationWebSocketHandlerTest {

    private NotificationWebSocketHandler handler;
    private int secuencia;

    @BeforeEach
    void setUp() {
        handler = new NotificationWebSocketHandler();
    }

    private WebSocketSession sesion(String usuarioId, String rol) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        if (usuarioId != null) attrs.put(NotificationWebSocketHandler.ATTR_USUARIO, usuarioId);
        if (rol != null) attrs.put(NotificationWebSocketHandler.ATTR_ROL, rol);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.getId()).thenReturn("s" + (++secuencia));
        when(s.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(s);
        return s;
    }

    @Test
    void unAvisoLlegaATodasLasPestanasDelUsuarioYANadieMas() throws Exception {
        WebSocketSession taller1 = sesion("u-taller", "TALLER");
        WebSocketSession taller2 = sesion("u-taller", "TALLER");
        WebSocketSession otroTaller = sesion("u-otro", "TALLER");

        handler.enviarAUsuario("u-taller", "{\"type\":\"notificacion\"}");

        verify(taller1).sendMessage(any(TextMessage.class));
        verify(taller2).sendMessage(any(TextMessage.class));
        verify(otroTaller, never()).sendMessage(any());
    }

    @Test
    void losEventosDelPersonalSoloLleganASusRoles() throws Exception {
        WebSocketSession admin = sesion("u-admin", "SUPERADMIN");
        WebSocketSession tienda = sesion("u-tienda", "TIENDA");
        WebSocketSession cliente = sesion("u-cliente", "CLIENTE");

        handler.enviarARoles(List.of("SUPERADMIN", "ADMIN", "VENDEDOR"), "{\"type\":\"solicitud\"}");

        verify(admin).sendMessage(any(TextMessage.class));
        verify(tienda, never()).sendMessage(any());
        verify(cliente, never()).sendMessage(any());
    }

    @Test
    void unaSesionSinUsuarioSeCierra() throws Exception {
        WebSocketSession anonima = sesion(null, null);

        verify(anonima).close(any(CloseStatus.class));
        assertEquals(0, handler.count());
    }

    @Test
    void alCerrarseLaSesionDejaDeRecibir() throws Exception {
        WebSocketSession s = sesion("u1", "TIENDA");
        handler.afterConnectionClosed(s, CloseStatus.NORMAL);

        handler.enviarAUsuario("u1", "{}");

        verify(s, never()).sendMessage(any());
        assertEquals(0, handler.count());
    }

    @Test
    void desactivarLaCuentaCierraSusConexiones() throws Exception {
        WebSocketSession s1 = sesion("u1", "TALLER");
        WebSocketSession s2 = sesion("u1", "TALLER");
        WebSocketSession otra = sesion("u2", "TALLER");

        handler.cerrarSesionesDe("u1");
        handler.enviarAUsuario("u1", "{}");

        verify(s1).close(any(CloseStatus.class));
        verify(s2).close(any(CloseStatus.class));
        verify(s1, never()).sendMessage(any());
        verify(otra, never()).close(any(CloseStatus.class));
        assertEquals(1, handler.count());
    }

    @Test
    void hayUnLimiteDePestanasPorCuenta() throws Exception {
        for (int i = 0; i < NotificationWebSocketHandler.MAX_SESIONES_POR_USUARIO; i++) sesion("u1", "TIENDA");

        WebSocketSession sobrante = sesion("u1", "TIENDA");

        verify(sobrante).close(any(CloseStatus.class));
        assertEquals(NotificationWebSocketHandler.MAX_SESIONES_POR_USUARIO, handler.count());
    }

    @Test
    void unaPestanaQueFallaNoImpideAvisarALasDemas() throws Exception {
        WebSocketSession rota = sesion("u1", "TALLER");
        doThrow(new IOException("conexión caída")).when(rota).sendMessage(any());
        WebSocketSession sana = sesion("u1", "TALLER");

        assertDoesNotThrow(() -> handler.enviarAUsuario("u1", "{}"));

        verify(sana).sendMessage(any(TextMessage.class));
    }
}
