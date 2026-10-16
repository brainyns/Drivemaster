package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.security.ChatRateLimiter;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Asistente de IA de la TIENDA (solo ese rol: lo define SecurityConfig). Responde sobre la operación de la propia
 * tienda y sugiere qué hacer, pero no cambia nada: solo lee. La tienda sale de la sesión y sus datos viajan a n8n
 * como contexto del mensaje; n8n no tiene herramientas aquí, así que el modelo no puede pedir datos de otra tienda.
 */
@RestController
@RequestMapping("/api/tiendas/mi-tienda/asistente")
@Slf4j
public class AsistenteTiendaController {

    private static final String MSG_ERROR =
            "El asistente no está disponible en este momento. Intenta de nuevo en unos segundos; tus informes siguen abajo.";
    private static final String PEDIDO_RESUMEN =
            "Hazme el resumen de hoy de mi tienda en máximo 5 líneas con guion: cómo van las ventas frente al mes anterior, "
                    + "lo más urgente por despachar, qué reponer primero, cuánto dinero tengo por liquidar y una oportunidad "
                    + "(producto sin movimiento o margen bajo). Sin saludo.";
    private static final int MAX_HISTORIAL = 6;
    private static final int MAX_TEXTO_HISTORIAL = 2_000;

    public record Turno(String rol, String texto) {}
    public record Pregunta(String mensaje, List<Turno> historial) {}
    public record Respuesta(String respuesta) {}

    private final AlcanceTienda alcanceTienda;
    private final ContextoAsistenteTienda contexto;
    private final ChatRateLimiter limitador;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String secreto;
    private final int maxCaracteres;
    private final RestTemplate restTemplate;

    // Un resumen por tienda y día: se genera al abrir Informes y no debe pagar el modelo en cada visita.
    private final Cache<String, String> resumenes = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .maximumSize(10_000)
            .build();

    public AsistenteTiendaController(AlcanceTienda alcanceTienda, ContextoAsistenteTienda contexto, ChatRateLimiter limitador,
                                     ObjectMapper objectMapper,
                                     @Value("${n8n.asistente-tienda.url:http://localhost:5678/webhook/asistente-tienda}") String webhookUrl,
                                     @Value("${n8n.chat.secret:}") String secreto,
                                     @Value("${n8n.chat.max-caracteres:500}") int maxCaracteres) {
        this.alcanceTienda = alcanceTienda;
        this.contexto = contexto;
        this.limitador = limitador;
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.secreto = secreto;
        this.maxCaracteres = Math.max(1, maxCaracteres);

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(90_000);
        this.restTemplate = new RestTemplate(fabrica);
    }

    @PostMapping
    public ResponseEntity<Respuesta> preguntar(@RequestBody Pregunta pregunta, Authentication auth) {
        if (pregunta == null || pregunta.mensaje() == null || pregunta.mensaje().isBlank()) {
            return ResponseEntity.badRequest().body(new Respuesta("Escribe una pregunta sobre tu tienda."));
        }
        if (pregunta.mensaje().length() > maxCaracteres) {
            return ResponseEntity.badRequest().body(new Respuesta(
                    "Tu pregunta es muy larga. Escríbela en máximo " + maxCaracteres + " caracteres."));
        }
        Tienda tienda = alcanceTienda.tienda(auth);
        // Mismo cupo que el chat público (el global acota el gasto diario), contado por tienda y no por IP.
        limitador.consumir("tienda:" + tienda.getId());
        String r = consultar(tienda, pregunta.mensaje(), historialSeguro(pregunta.historial()));
        return ResponseEntity.ok(new Respuesta(r == null ? MSG_ERROR : r));
    }

    @GetMapping("/resumen")
    public ResponseEntity<Respuesta> resumen(Authentication auth) {
        Tienda tienda = alcanceTienda.tienda(auth);
        String clave = tienda.getId() + "|" + LocalDate.now(ZoneId.of("America/Bogota"));
        String guardado = resumenes.getIfPresent(clave);
        if (guardado != null) return ResponseEntity.ok(new Respuesta(guardado));

        limitador.consumir("tienda:" + tienda.getId());
        String r = consultar(tienda, PEDIDO_RESUMEN, List.of());
        if (r == null) return ResponseEntity.ok(new Respuesta(MSG_ERROR));
        resumenes.put(clave, r);   // los errores no se guardan: el siguiente intento vuelve a probar
        return ResponseEntity.ok(new Respuesta(r));
    }

    /** Envía la pregunta con los datos de la tienda a n8n; null si n8n falla o responde vacío. */
    private String consultar(Tienda tienda, String mensaje, List<Map<String, String>> historial) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (secreto != null && !secreto.isBlank()) headers.set("X-Chat-Secret", secreto);

            Map<String, Object> cuerpo = Map.of(
                    "mensaje", mensaje,
                    "historial", historial,
                    // Texto y no objeto: n8n lo pega tal cual en el mensaje de sistema.
                    "contexto", objectMapper.writeValueAsString(contexto.contexto(tienda)));

            ResponseEntity<Map<String, Object>> r = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, new HttpEntity<>(cuerpo, headers), new ParameterizedTypeReference<>() {});
            Object texto = r.getBody() == null ? null : r.getBody().get("respuesta");
            return texto != null && !texto.toString().isBlank() ? texto.toString() : null;
        } catch (Exception e) {
            log.error("Error llamando al asistente de tienda en n8n ({}): {}", webhookUrl, e.getMessage());
            return null;
        }
    }

    /** El historial lo manda el navegador: solo los últimos turnos, con rol conocido y texto acotado. */
    static List<Map<String, String>> historialSeguro(List<Turno> historial) {
        if (historial == null) return List.of();
        List<Map<String, String>> limpio = historial.stream()
                .filter(t -> t != null && t.texto() != null && !t.texto().isBlank()
                        && ("usuario".equals(t.rol()) || "asistente".equals(t.rol())))
                .map(t -> Map.of(
                        "role", "usuario".equals(t.rol()) ? "user" : "assistant",
                        "content", t.texto().length() > MAX_TEXTO_HISTORIAL ? t.texto().substring(0, MAX_TEXTO_HISTORIAL) : t.texto()))
                .collect(Collectors.toList());
        return limpio.subList(Math.max(0, limpio.size() - MAX_HISTORIAL), limpio.size());
    }
}
