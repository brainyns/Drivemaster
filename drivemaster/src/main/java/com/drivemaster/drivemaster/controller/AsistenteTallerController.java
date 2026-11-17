package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.controller.AsistenteTiendaController.Detras;
import com.drivemaster.drivemaster.controller.AsistenteTiendaController.Pregunta;
import com.drivemaster.drivemaster.controller.AsistenteTiendaController.RespuestaChat;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.security.ChatRateLimiter;
import com.drivemaster.drivemaster.service.AsistenteTiendaIA;
import com.drivemaster.drivemaster.service.AsistenteTiendaIA.Resultado;
import com.drivemaster.drivemaster.service.ContextoAsistenteTaller;
import com.drivemaster.drivemaster.service.TallerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * IA del TALLER (solo ese rol: /api/talleres/mi-taller/** en SecurityConfig). Igual que la de la tienda: solo lee y
 * sugiere. El taller sale de la sesión y sus datos viajan a n8n como contexto; allí el modelo no tiene herramientas.
 */
@RestController
@RequestMapping("/api/talleres/mi-taller/asistente")
public class AsistenteTallerController {

    private static final String MSG_CHAT_ERROR =
            "El asistente no está disponible en este momento. Intenta de nuevo en unos segundos.";
    private static final int MAX_MENSAJE = 500;

    private final TallerService tallerService;
    private final ContextoAsistenteTaller contexto;
    private final AsistenteTiendaIA ia;
    private final ChatRateLimiter limitador;

    public AsistenteTallerController(TallerService tallerService, ContextoAsistenteTaller contexto, AsistenteTiendaIA ia,
                                     ChatRateLimiter limitador) {
        this.tallerService = tallerService;
        this.contexto = contexto;
        this.ia = ia;
        this.limitador = limitador;
    }

    @PostMapping
    public RespuestaChat preguntar(@RequestBody Pregunta pregunta, Authentication auth) {
        if (pregunta == null || pregunta.mensaje() == null || pregunta.mensaje().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe una pregunta sobre tu taller.");
        }
        if (pregunta.mensaje().length() > MAX_MENSAJE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tu pregunta es muy larga. Escríbela en máximo " + MAX_MENSAJE + " caracteres.");
        }
        Taller taller = tallerService.obtenerPorUsuario(auth.getName());
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller aún no está aprobado");
        }
        // Mismo cupo que el chat público (el global acota el gasto diario), contado por taller.
        limitador.consumir("taller:" + taller.getId());
        Map<String, Object> c = contexto.contexto(taller, auth.getName());
        Resultado r = ia.conversarTaller(c, pregunta.mensaje(), AsistenteTiendaController.historialSeguro(pregunta.historial()),
                datosMostrados(c));
        Detras detras = new Detras(List.of(
                "Taller tomado de tu sesión: " + taller.getNombre() + ". Lo que escribas no puede cambiarlo.",
                "El modelo solo recibió datos de este taller, sin nombres, teléfonos, placas ni direcciones de clientes.",
                "Solo lectura: no puede aceptar solicitudes, agendar, cotizar, comprar ni cambiar precios.",
                "Tu conversación se recortó a los últimos 6 mensajes antes de enviarse."),
                r.modelo(), r.tokensEntrada(), r.tokensSalida(), r.ms(),
                "Lectura de los datos de tu taller, calculados por DriveMaster antes de preguntar al modelo", r.datos());
        return new RespuestaChat(r.texto() == null ? MSG_CHAT_ERROR : r.texto(), r.texto() == null, detras);
    }

    /** Lo que vio el modelo, en palabras del taller y con cantidades. */
    static List<String> datosMostrados(Map<String, Object> c) {
        int citas = c.get("citasProximos7Dias") instanceof List<?> l ? l.size() : 0;
        int enCamino = c.get("pedidosEnCamino") instanceof List<?> l ? l.size() : 0;
        return List.of(
                "Informes de los últimos 6 meses: servicios, ingresos, tiempo de respuesta y calificación",
                c.get("solicitudesPorResponder") + " solicitudes esperando tu respuesta",
                citas + " citas en los próximos 7 días",
                c.get("piezasEnInventario") + " piezas en tu inventario (se envían máximo 40, primero las de stock bajo)",
                enCamino + " pedidos de repuestos en camino");
    }
}
