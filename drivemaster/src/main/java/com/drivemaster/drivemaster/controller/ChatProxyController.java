package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.ChatRequest;
import com.drivemaster.drivemaster.dto.ChatResponse;
import com.drivemaster.drivemaster.dto.ProductoDTO;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.security.ChatRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Proxy del chat hacia n8n. Spring ya no contiene lógica de IA: el antiguo
 * GeminiServiceImpl (intenciones por regex, caché, rate limiter y llamadas
 * directas a Gemini) se eliminó. Aquí solo se valida la entrada, se limita por
 * IP y se reenvía al webhook de n8n con el secreto compartido.
 *
 * Se mantiene el contrato {respuesta, productos} para que el widget React no
 * cambie; si n8n falla, se responde con un mensaje amable (mismo comportamiento
 * que el chat anterior).
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatProxyController {

    private static final String MSG_ERROR =
            "Lo siento, el asistente no está disponible en este momento. Por favor, intenta de nuevo en unos segundos.";
    private static final String MSG_VACIO = "Escribe un mensaje para poder ayudarte.";

    /** Las tarjetas del chat muestran como mucho 3 productos, igual que el texto del agente. */
    private static final int MAX_PRODUCTOS = 3;

    private final ChatRateLimiter chatRateLimiter;
    private final ProductoRepository productoRepository;
    private final ObjectMapper objectMapper;
    private final String n8nWebhookUrl;
    private final String n8nChatSecret;
    private final RestTemplate chatRestTemplate;
    private final int maxCaracteres;

    public ChatProxyController(ChatRateLimiter chatRateLimiter,
                               ProductoRepository productoRepository,
                               ObjectMapper objectMapper,
                               @Value("${n8n.webhook.url:http://localhost:5678/webhook/chat-drivemaster}") String n8nWebhookUrl,
                               @Value("${n8n.chat.secret:}") String n8nChatSecret,
                               @Value("${n8n.chat.max-caracteres:500}") int maxCaracteres) {
        this.chatRateLimiter = chatRateLimiter;
        this.productoRepository = productoRepository;
        this.objectMapper = objectMapper;
        this.n8nWebhookUrl = n8nWebhookUrl;
        this.n8nChatSecret = n8nChatSecret;
        this.maxCaracteres = Math.max(1, maxCaracteres);

        // El agente de n8n (modelo + herramientas) puede tardar: conecta pronto, lee con holgura.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(90_000);
        this.chatRestTemplate = new RestTemplate(fabrica);
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpServletRequest http) {
        // No se lee X-Forwarded-For a mano: su primer valor lo escribe el cliente y bastaba con
        // cambiarlo para saltarse el límite. Con server.forward-headers-strategy=native, Tomcat
        // (RemoteIpValve) ya resuelve la IP real recorriendo la cabecera desde los proxies internos
        // (nginx, cloudflared), así que getRemoteAddr() es la IP del cliente.
        String ip = http.getRemoteAddr();

        if (request == null || request.getMensaje() == null || request.getMensaje().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse(MSG_VACIO, Collections.emptyList()));
        }
        // Un mensaje enorme se paga en cada llamada al modelo y queda en la memoria de la sesión.
        if (request.getMensaje().length() > maxCaracteres) {
            return ResponseEntity.badRequest().body(new ChatResponse(
                    "Tu mensaje es muy largo. Escríbelo en máximo " + maxCaracteres + " caracteres.", Collections.emptyList()));
        }
        // Se cuenta después de validar: un mensaje rechazado no gasta el cupo.
        chatRateLimiter.consumir(ip);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (n8nChatSecret != null && !n8nChatSecret.isBlank()) {
                headers.set("X-Chat-Secret", n8nChatSecret);
            }

            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("mensaje", request.getMensaje());
            cuerpo.put("sessionId", (request.getSessionId() == null || request.getSessionId().isBlank())
                    ? "anon-" + ip : request.getSessionId());

            ResponseEntity<Map<String, Object>> respuesta = chatRestTemplate.exchange(
                    n8nWebhookUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(cuerpo, headers),
                    new ParameterizedTypeReference<>() {});

            Map<String, Object> cuerpoRespuesta = respuesta.getBody();
            return ResponseEntity.ok(new ChatResponse(extraerTexto(cuerpoRespuesta), extraerProductos(cuerpoRespuesta)));
        } catch (Exception e) {
            // 200 con mensaje amable: el widget del frontend muestra el texto del bot,
            // igual que hacía el chat anterior ante errores de Gemini.
            log.error("Error llamando a n8n ({}): {}", n8nWebhookUrl, e.getMessage());
            return ResponseEntity.ok(new ChatResponse(MSG_ERROR, Collections.emptyList()));
        }
    }

    /** n8n responde {respuesta, productos}; se tolera también un texto crudo en "output". */
    private String extraerTexto(Map<String, Object> cuerpo) {
        if (cuerpo == null) return MSG_ERROR;
        Object r = cuerpo.get("respuesta");
        if (r == null) r = cuerpo.get("output");
        return (r != null && !r.toString().isBlank()) ? r.toString() : MSG_ERROR;
    }

    private List<ProductoDTO> extraerProductos(Map<String, Object> cuerpo) {
        try {
            Object p = cuerpo == null ? null : cuerpo.get("productos");
            if (p instanceof List<?> lista) {
                List<ProductoDTO> productos = lista.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> objectMapper.convertValue(item, ProductoDTO.class))
                        .filter(dto -> dto.getId() != null)
                        .limit(MAX_PRODUCTOS)
                        .collect(Collectors.toList());
                return completarDesdeCatalogo(productos);
            }
        } catch (Exception e) {
            log.warn("No se pudieron leer los productos devueltos por n8n: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * La herramienta del agente no envía la foto (gasta tokens) y lo que llega de n8n no es de fiar
     * para precio y stock: se leen de la base. Se descartan los productos inactivos o que ya no existen.
     */
    private List<ProductoDTO> completarDesdeCatalogo(List<ProductoDTO> productos) {
        if (productos.isEmpty()) return productos;
        Map<String, Producto> porId = productoRepository.findAllById(
                        productos.stream().map(ProductoDTO::getId).toList())
                .stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity(), (a, b) -> a));
        return productos.stream()
                .map(dto -> {
                    Producto real = porId.get(dto.getId());
                    if (real == null || Boolean.FALSE.equals(real.getActivo())) return null;
                    dto.setNombre(real.getNombre());
                    dto.setMarca(real.getMarca());
                    dto.setPrecioVenta(real.getPrecioVenta());
                    dto.setStockActual(real.getStockActual());
                    dto.setTipo(real.getTipo());
                    dto.setDiasEntregaEncargo(real.getDiasEntregaEncargo());
                    dto.setImagenUrl(real.getImagenUrl());
                    // La tarjeta no usa la compatibilidad: no se envía al navegador.
                    dto.setModelosCompatibles(null);
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
