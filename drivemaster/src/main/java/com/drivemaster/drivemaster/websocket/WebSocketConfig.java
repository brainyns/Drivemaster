package com.drivemaster.drivemaster.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.drivemaster.drivemaster.security.SecurityConfig;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final TicketHandshakeInterceptor ticketInterceptor;

    public WebSocketConfig(NotificationWebSocketHandler notificationHandler, TicketHandshakeInterceptor ticketInterceptor) {
        this.notificationHandler = notificationHandler;
        this.ticketInterceptor = ticketInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Se conecta con un ticket de un solo uso (?ticket=...), nunca con el JWT en la URL
        registry.addHandler(notificationHandler, "/ws/notificaciones")
                .addInterceptors(ticketInterceptor)
                .setAllowedOriginPatterns(SecurityConfig.ORIGENES_PERMITIDOS.toArray(String[]::new));
    }
}
