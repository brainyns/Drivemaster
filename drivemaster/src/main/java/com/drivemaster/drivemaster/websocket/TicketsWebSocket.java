package com.drivemaster.drivemaster.websocket;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tickets para abrir el WebSocket sin poner el JWT en la URL. El frontend pide uno con su sesión
 * (POST /api/notificaciones/ticket) y lo usa enseguida: vale 30 segundos y una sola vez.
 * Viven en memoria: con varias instancias del backend, el ticket debe canjearse en la que lo emitió.
 */
@Component
public class TicketsWebSocket {

    static final Duration VIGENCIA = Duration.ofSeconds(30);

    private record Entrada(String usuarioId, Instant expira) {}

    private final Map<String, Entrada> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TicketsWebSocket() {
        this(Clock.systemUTC());
    }

    TicketsWebSocket(Clock clock) {
        this.clock = clock;
    }

    public String emitir(String usuarioId) {
        purgarVencidos();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Entrada(usuarioId, clock.instant().plus(VIGENCIA)));
        return ticket;
    }

    /** Devuelve el dueño del ticket y lo invalida; vacío si no existe, ya se usó o venció. */
    public Optional<String> canjear(String ticket) {
        if (ticket == null || ticket.isBlank()) return Optional.empty();
        Entrada entrada = tickets.remove(ticket);
        if (entrada == null || !clock.instant().isBefore(entrada.expira())) return Optional.empty();
        return Optional.of(entrada.usuarioId());
    }

    public long segundosDeVigencia() {
        return VIGENCIA.toSeconds();
    }

    private void purgarVencidos() {
        Instant ahora = clock.instant();
        tickets.values().removeIf(e -> !ahora.isBefore(e.expira()));
    }
}
