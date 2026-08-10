package com.drivemaster.drivemaster.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.drivemaster.drivemaster.security.JwtUtil;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private final JwtUtil jwtUtil;

    public NotificationWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = session.getUri() != null
                ? session.getUri().getQuery()
                : null;
        if (token != null && token.startsWith("token=")) {
            token = token.substring("token=".length());
        }

        boolean valido = false;
        if (token != null && !token.isBlank()) {
            try {
                valido = jwtUtil.extractUsername(token) != null && !jwtUtil.isTokenExpired(token);
            } catch (Exception e) {
                valido = false;
            }
        }

        if (!valido) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Token inválido"));
            return;
        }

        sessions.add(session);
        log.info("WebSocket conectado: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket desconectado: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // solo broadcast del servidor
    }

    public void broadcast(String json) {
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            try {
                synchronized (session) {
                    session.sendMessage(msg);
                }
            } catch (IOException e) {
                log.warn("No se pudo enviar notificación a la sesión {}", session.getId());
            }
        }
    }

    public int count() {
        return sessions.size();
    }
}
