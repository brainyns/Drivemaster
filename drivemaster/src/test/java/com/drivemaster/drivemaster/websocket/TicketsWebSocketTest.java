package com.drivemaster.drivemaster.websocket;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** El ticket del WebSocket vale una sola vez y solo durante 30 segundos. */
class TicketsWebSocketTest {

    /** Reloj que se puede adelantar a mano. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-12T10:00:00Z");
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final RelojManual reloj = new RelojManual();
    private final TicketsWebSocket tickets = new TicketsWebSocket(reloj);

    @Test
    void unTicketSeCanjeaUnaSolaVez() {
        String ticket = tickets.emitir("u1");

        assertEquals(Optional.of("u1"), tickets.canjear(ticket));
        assertEquals(Optional.empty(), tickets.canjear(ticket));
    }

    @Test
    void unTicketVencidoNoSirve() {
        String ticket = tickets.emitir("u1");
        reloj.avanzar(TicketsWebSocket.VIGENCIA);

        assertEquals(Optional.empty(), tickets.canjear(ticket));
    }

    @Test
    void justoAntesDeVencerTodaviaSirve() {
        String ticket = tickets.emitir("u1");
        reloj.avanzar(TicketsWebSocket.VIGENCIA.minusMillis(1));

        assertEquals(Optional.of("u1"), tickets.canjear(ticket));
    }

    @Test
    void ticketsVaciosODesconocidosNoSirven() {
        assertEquals(Optional.empty(), tickets.canjear(null));
        assertEquals(Optional.empty(), tickets.canjear(" "));
        assertEquals(Optional.empty(), tickets.canjear("inventado"));
    }

    @Test
    void cadaTicketEsDistintoYLargo() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = tickets.emitir("u1");
            assertTrue(t.length() >= 40, t);
            assertTrue(vistos.add(t));
        }
    }
}
