package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.Notificacion;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.NotificacionService;
import com.drivemaster.drivemaster.websocket.TicketsWebSocket;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Campanita: avisos propios del usuario en sesión y ticket para abrir el WebSocket. */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificaciones;
    private final TicketsWebSocket tickets;
    private final UsuarioRepository usuarioRepository;

    public NotificacionController(NotificacionService notificaciones, TicketsWebSocket tickets,
                                  UsuarioRepository usuarioRepository) {
        this.notificaciones = notificaciones;
        this.tickets = tickets;
        this.usuarioRepository = usuarioRepository;
    }

    /** Ticket de un solo uso (30 s) para conectar a /ws/notificaciones?ticket=... */
    @PostMapping("/ticket")
    public Map<String, Object> ticket(Authentication auth) {
        Usuario usuario = usuario(auth);
        return Map.of("ticket", tickets.emitir(usuario.getId()), "expiraEnSegundos", tickets.segundosDeVigencia());
    }

    @GetMapping("/mias")
    public Map<String, Object> mias(@RequestParam(required = false) Integer limite, Authentication auth) {
        String usuarioId = usuario(auth).getId();
        List<Notificacion> items = notificaciones.mias(usuarioId, limite);
        return Map.of("items", items, "noLeidas", notificaciones.noLeidas(usuarioId));
    }

    @PatchMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable String id, Authentication auth) {
        if (!notificaciones.marcarLeida(usuario(auth).getId(), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada");
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/mias/leidas")
    public Map<String, Object> marcarTodas(Authentication auth) {
        return Map.of("marcadas", notificaciones.marcarTodasLeidas(usuario(auth).getId()));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByCorreo(auth.getName())
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión"));
    }
}
