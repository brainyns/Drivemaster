package com.drivemaster.drivemaster.websocket;

import org.springframework.beans.factory.annotation.Value;
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
    private final String frontendUrl;

    public WebSocketConfig(NotificationWebSocketHandler notificationHandler, TicketHandshakeInterceptor ticketInterceptor,
                           @Value("${frontend.url:}") String frontendUrl) {
        this.notificationHandler = notificationHandler;
        this.ticketInterceptor = ticketInterceptor;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Se conecta con un ticket de un solo uso (?ticket=...), nunca con el JWT en la URL
        registry.addHandler(notificationHandler, "/ws/notificaciones")
                .addInterceptors(ticketInterceptor)
                .setAllowedOriginPatterns(SecurityConfig.origenesPermitidos(frontendUrl).toArray(String[]::new));
    }
}
