package com.drivemaster.drivemaster.websocket;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Sesiones de la campanita, agrupadas por usuario. Cada aviso va solo a su destinatario
 * (o a los roles indicados): nunca se reparte a todos los conectados.
 * La identidad la pone {@link TicketHandshakeInterceptor} en los atributos de la sesión.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USUARIO = "usuarioId";
    public static final String ATTR_ROL = "rol";
    /** Pestañas abiertas por cuenta; más allá se rechaza la conexión. */
    static final int MAX_SESIONES_POR_USUARIO = 10;
    private static final int LIMITE_ENVIO_MS = 5_000;
    private static final int LIMITE_BUFFER_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    /** Sesión decorada (envíos seguros entre hilos) por id de sesión. */
    private final Map<String, WebSocketSession> sesiones = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sesionesPorUsuario = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String usuarioId = (String) session.getAttributes().get(ATTR_USUARIO);
        if (usuarioId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("No autorizado"));
            return;
        }
        Set<String> propias = sesionesPorUsuario.computeIfAbsent(usuarioId, k -> ConcurrentHashMap.newKeySet());
        if (propias.size() >= MAX_SESIONES_POR_USUARIO) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Demasiadas conexiones"));
            return;
        }
        sesiones.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, LIMITE_ENVIO_MS, LIMITE_BUFFER_BYTES));
        propias.add(session.getId());
        log.debug("WebSocket conectado: {} (usuario {})", session.getId(), usuarioId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        quitar(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // el servidor solo envía; lo que mande el cliente se ignora
    }

    /** Envía a todas las pestañas abiertas del usuario. */
    public void enviarAUsuario(String usuarioId, String json) {
        Set<String> ids = usuarioId != null ? sesionesPorUsuario.get(usuarioId) : null;
        if (ids == null) return;
        TextMessage msg = new TextMessage(json);
        for (String id : ids) enviar(sesiones.get(id), msg);
    }

    /** Envía a las sesiones de cuentas con alguno de esos roles. */
    public void enviarARoles(Collection<String> roles, String json) {
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sesiones.values()) {
            if (roles.contains((String) s.getAttributes().get(ATTR_ROL))) enviar(s, msg);
        }
    }

    /** Cierra las conexiones de una cuenta (por ejemplo, al desactivarla). */
    public void cerrarSesionesDe(String usuarioId) {
        Set<String> ids = usuarioId != null ? sesionesPorUsuario.remove(usuarioId) : null;
        if (ids == null) return;
        for (String id : ids) {
            WebSocketSession s = sesiones.remove(id);
            if (s == null) continue;
            try {
                s.close(CloseStatus.POLICY_VIOLATION.withReason("Cuenta desactivada"));
            } catch (IOException e) {
                log.debug("No se pudo cerrar la sesión {}", id);
            }
        }
    }

    public int count() {
        return sesiones.size();
    }

    private void enviar(WebSocketSession session, TextMessage msg) {
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(msg);
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo enviar la notificación a la sesión {}", session.getId());
        }
    }

    private void quitar(WebSocketSession session) {
        sesiones.remove(session.getId());
        String usuarioId = (String) session.getAttributes().get(ATTR_USUARIO);
        if (usuarioId == null) return;
        sesionesPorUsuario.computeIfPresent(usuarioId, (k, ids) -> {
            ids.remove(session.getId());
            return ids.isEmpty() ? null : ids;
        });
    }
}
