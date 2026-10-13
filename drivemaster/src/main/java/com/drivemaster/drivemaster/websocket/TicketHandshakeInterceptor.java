package com.drivemaster.drivemaster.websocket;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Autoriza la conexión con un ticket de un solo uso (?ticket=...). El ticket solo se emite con un token
 * de acceso válido de una cuenta activa (el filtro JWT rechaza los refresh tokens y las cuentas
 * desactivadas); aquí se vuelve a comprobar que la cuenta siga activa al conectar.
 */
@Component
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    private final TicketsWebSocket tickets;
    private final UsuarioRepository usuarioRepository;

    public TicketHandshakeInterceptor(TicketsWebSocket tickets, UsuarioRepository usuarioRepository) {
        this.tickets = tickets;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
        Usuario usuario = tickets.canjear(ticket)
                .flatMap(usuarioRepository::findById)
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .orElse(null);
        if (usuario == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(NotificationWebSocketHandler.ATTR_USUARIO, usuario.getId());
        attributes.put(NotificationWebSocketHandler.ATTR_ROL, usuario.getRol());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nada
    }
}
