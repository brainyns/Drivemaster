package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AiIntentResponse;
import com.drivemaster.drivemaster.dto.ChatRequest;
import com.drivemaster.drivemaster.dto.ChatResponse;
import com.drivemaster.drivemaster.dto.ProductoDTO;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GeminiServiceImpl implements GeminiService {

    private static final Set<String> CATEGORIAS = Set.of(
            "filtro", "freno", "aceite", "bujía", "batería", "amortiguador",
            "embrague", "alternador", "arranque", "sensor", "inyector", "bomba",
            "disco", "bande", "caliper", "homocinetica", "junta", "reten", "balata"
    );

    private static final Set<String> MARCAS = Set.of(
            "toyota", "hyundai", "kia", "mazda", "nissan", "mitsubishi", "chevrolet", "ford",
            "bmw", "mercedes", "honda", "volkswagen", "audi", "jeep", "peugeot", "renault",
            "citroën", "fiat", "subaru", "suzuki"
    );

    private static final Set<String> NEGOCIO_KEYWORDS = Set.of(
            "repuesto", "producto", "inventario", "stock", "precio", "costo", "compra", "venta",
            "tienda", "vehículo", "vehiculo", "auto", "carro", "camión", "camion", "modelo",
            "año", "marca", "categoría", "existencia", "disponible", "cuanto cuesta", "carrocería",
            "suspensión", "dirección", "escape", "transmisión", "caja", "marcha"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "para", "que", "cual", "donde", "como", "del", "los", "las", "una", "unos",
            "con", "por", "este", "esta", "ese", "esa", "tiene", "hay", "ver", "dame", "necesito"
    );

    private static final Map<String, List<Pattern>> INTENT_PATTERNS = new LinkedHashMap<>();
    static {
        INTENT_PATTERNS.put("saludo", List.of(
                Pattern.compile("(?i)\\b(hola|buenos días|buenas tardes|buenas noches|saludos|qué tal|hello|hi)\\b")));
        INTENT_PATTERNS.put("despedida", List.of(
                Pattern.compile("(?i)\\b(adiós|hasta luego|gracias|nos vemos|bye)\\b")));
        INTENT_PATTERNS.put("informacion", List.of(
                Pattern.compile("(?i)\\b(horario|horas|abren|cierran|ubicación|dónde|atención)\\b")));
        INTENT_PATTERNS.put("buscar_producto", List.of(
                Pattern.compile("(?i).*\\b(" + String.join("|", CATEGORIAS) + ")\\b.*")));
        INTENT_PATTERNS.put("buscar_marca", List.of(
                Pattern.compile("(?i).*\\b(" + String.join("|", MARCAS) + ")\\b.*"),
                Pattern.compile("(?i).*\\b(compatible|modelo|año|vehículo|auto|carro|camión|suv|versión|cylindraje|cc)\\b.*")));
        INTENT_PATTERNS.put("consultar_precio", List.of(
                Pattern.compile("(?i).*\\b(tiene|hay|disponible|stock|existencia|cuánto cuesta|precio|costo|venta|vender)\\b.*")));
        INTENT_PATTERNS.put("recomendar", List.of(
                Pattern.compile("(?i).*\\b(recomienda|mejor|sugiere|qué me recomiendas|cuál es mejor|ideal|perfecto)\\b.*")));
        INTENT_PATTERNS.put("consulta_tecnica", List.of(
                Pattern.compile("(?i).*\\b(cómo|cuál|para qué|compatible|sirve para|cual para)\\b.*")));
    }

    private static final List<Pattern> COMPARATIVE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(qué pasa si|que pasa si|qué tal si|que tal si|mejor|peor|recomiend|conviene|vale la pena|diferencia|más\\s+económic|más\\s+barat|más\\s+caro|ventajas|desventajas)\\b")
    );

    private static final String MSG_SALUDO = "¡Hola! 👋 Bienvenido a Drivemaster. ¿En qué puedo ayudarte hoy?";
    private static final String MSG_DESPEDIDA = "¡Hasta luego! 👋 Que tengas un excelente día.";
    private static final String MSG_INFO = "📍 Estamos ubicados en la ciudad. Horario: lunes a sábado de 8:00 AM a 6:00 PM.";
    private static final String MSG_FUERA_ALCANCE = "Lo siento, solo puedo ayudarte con temas relacionados con repuestos automotrices, vehículos, nuestro inventario, precios, disponibilidad y servicios de la tienda. ¿Hay algo específico sobre repuestos en lo que pueda ayudarte?";
    private static final String MSG_ERROR = "Lo siento, ocurrió un error al procesar tu solicitud. Por favor, intenta de nuevo.";

    private final ProductoRepository productoRepository;
    private final RestTemplate restTemplate;
    private final String geminiApiKey;
    private final String geminiModel;
    private final long rateLimitMs;

    // Rate limiter con Semaphore
    private final Semaphore rateLimiter = new Semaphore(1);

    // Cache simple con ConcurrentHashMap y TTL
    private final Map<String, CachedChatData> responseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleaner = Executors.newSingleThreadScheduledExecutor();
    private static final int MAX_CACHE_SIZE = 100;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutos

    public GeminiServiceImpl(
            ProductoRepository productoRepository,
            RestTemplate restTemplate,
            @Value("${gemini.api.key}") String geminiApiKey,
            @Value("${gemini.model}") String geminiModel,
            @Value("${gemini.rate.limit.ms:4000}") long rateLimitMs) {
        this.productoRepository = productoRepository;
        this.restTemplate = restTemplate;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.rateLimitMs = rateLimitMs;

        // Limpiar caché periódicamente
        cacheCleaner.scheduleAtFixedRate(() -> {
            long ahora = System.currentTimeMillis();
            responseCache.entrySet().removeIf(entry -> ahora - entry.getValue().timestamp > CACHE_TTL_MS);
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public ChatResponse processChatRequest(ChatRequest request) {
        try {
            String mensaje = request.getMensaje();
            log.info("Procesando mensaje: {}", mensaje);
            String cacheKey = mensaje.toLowerCase().trim();

            // 1. Verificar caché
            CachedChatData cached = responseCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                log.info("Cache hit");
                return new ChatResponse(cached.responseText, cached.products);
            }

            // 2. Detectar intención local
            String intencion = detectarIntencion(mensaje);
            log.info("Intención detectada: {}", intencion);

            // 3. Buscar productos relacionados
            List<ProductoDTO> productos = (intencion != null && (intencion.equals("saludo") || intencion.equals("despedida")))
                    ? Collections.emptyList()
                    : buscarProductosReales(mensaje);

            // 4. Generar respuesta
            String respuesta;
            if (intencion == null) {
                if (!esConsultaDelNegocio(mensaje)) {
                    respuesta = MSG_FUERA_ALCANCE;
                    productos = Collections.emptyList();
                } else {
                    respuesta = llamarGeminiConRateLimit(mensaje);
                }
            } else {
                if (esIntencionDeProducto(intencion) && esConsultaComparativa(mensaje)) {
                    log.info("Consulta comparativa detectada, usando Gemini...");
                    respuesta = llamarGeminiConRateLimit(mensaje);
                } else {
                    respuesta = switch (intencion) {
                        case "saludo" -> MSG_SALUDO;
                        case "despedida" -> MSG_DESPEDIDA;
                        case "informacion" -> MSG_INFO;
                        default -> generarRespuestaProductos(intencion, mensaje, productos);
                    };
                }
            }

            // 5. Combinar respuesta con productos
            if (!productos.isEmpty() && intencion != null
                    && !intencion.equals("saludo") && !intencion.equals("despedida")) {
                respuesta = combinarRespuestaConProductos(respuesta, productos);
            }

            // 6. Guardar en caché
            if (responseCache.size() >= MAX_CACHE_SIZE) {
                String firstKey = responseCache.keySet().iterator().next();
                responseCache.remove(firstKey);
            }
            responseCache.put(cacheKey, new CachedChatData(respuesta, productos));

            return new ChatResponse(respuesta, productos);

        } catch (Exception e) {
            log.error("Error en processChatRequest", e);
            return new ChatResponse(MSG_ERROR, Collections.emptyList());
        }
    }

    @Override
    public AiIntentResponse interpretIntent(String mensaje) {
        AiIntentResponse resp = new AiIntentResponse();
        String intencion = detectarIntencion(mensaje);
        if (intencion != null) {
            resp.setIntent(intencion);
            resp.setCategoria(extraerCategoria(mensaje));
            resp.setMarca(extraerMarca(mensaje));
        } else {
            resp.setIntent("ia_necesaria");
        }
        return resp;
    }

    private String detectarIntencion(String mensaje) {
        for (var entry : INTENT_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(mensaje).find()) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean esIntencionDeProducto(String intencion) {
        return intencion.equals("buscar_producto") || intencion.equals("buscar_marca")
                || intencion.equals("recomendar") || intencion.equals("consulta_tecnica")
                || intencion.equals("consultar_precio");
    }

    private boolean esConsultaComparativa(String mensaje) {
        return COMPARATIVE_PATTERNS.stream().anyMatch(p -> p.matcher(mensaje).find());
    }

    private boolean esConsultaDelNegocio(String mensaje) {
        String msg = mensaje.toLowerCase();
        return CATEGORIAS.stream().anyMatch(msg::contains) ||
                MARCAS.stream().anyMatch(msg::contains) ||
                NEGOCIO_KEYWORDS.stream().anyMatch(msg::contains);
    }

    private String extraerCategoria(String mensaje) {
        String msg = mensaje.toLowerCase();
        return CATEGORIAS.stream()
                .filter(msg::contains)
                .findFirst()
                .map(c -> c.substring(0, 1).toUpperCase() + c.substring(1))
                .orElse("");
    }

    private String extraerMarca(String mensaje) {
        String msg = mensaje.toLowerCase();
        return MARCAS.stream()
                .filter(msg::contains)
                .findFirst()
                .map(m -> m.substring(0, 1).toUpperCase() + m.substring(1))
                .orElse("");
    }

    private List<ProductoDTO> buscarProductosReales(String mensaje) {
        Set<String> terminos = new HashSet<>();

        String cat = extraerCategoria(mensaje);
        if (!cat.isEmpty()) terminos.add(cat.toLowerCase());
        String marca = extraerMarca(mensaje);
        if (!marca.isEmpty()) terminos.add(marca.toLowerCase());

        String msgLimpio = mensaje.toLowerCase().replaceAll("[^a-záéíóúüñ0-9\\s]", " ");
        Arrays.stream(msgLimpio.split("\\s+"))
                .filter(w -> w.length() >= 2)
                .filter(w -> !STOP_WORDS.contains(w))
                .forEach(terminos::add);

        log.info("Términos de búsqueda: {}", terminos);
        if (terminos.isEmpty()) return Collections.emptyList();

        List<Producto> resultados = productoRepository.searchByTerms(terminos.stream().collect(Collectors.toList()));
        return resultados.stream()
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    private String generarRespuestaProductos(String intencion, String mensaje, List<ProductoDTO> productos) {
        if (productos.isEmpty()) {
            return generarMensajeNoEncontrado(mensaje);
        }
        if (productos.size() == 1) {
            ProductoDTO p = productos.get(0);
            return String.format(
                    "¡Encontré este producto para ti! 🎉\n\n📦 %s\n🏷️ Marca: %s\n📂 Categoría: %s\n💰 Precio: $%.2f\n📊 Stock: %d unidades%s",
                    p.getNombre(), p.getMarca(), p.getCategoria(), p.getPrecioVenta(), p.getStockActual(),
                    p.getCodigo() != null ? "\n🔖 Código: " + p.getCodigo() : "");
        } else {
            StringBuilder sb = new StringBuilder("¡Encontré " + productos.size() + " productos! 🎉\n\n");
            int limit = Math.min(productos.size(), 5);
            for (int i = 0; i < limit; i++) {
                ProductoDTO p = productos.get(i);
                sb.append(i + 1).append(". ").append(p.getNombre())
                        .append(" (").append(p.getMarca()).append(")\n   💰 $")
                        .append(String.format("%.2f", p.getPrecioVenta()))
                        .append(" | 📦 ").append(p.getStockActual()).append(" disponible\n\n");
            }
            if (productos.size() > limit) {
                sb.append("... y ").append(productos.size() - limit).append(" productos más.");
            }
            return sb.toString();
        }
    }

    private String generarMensajeNoEncontrado(String mensaje) {
        String categoria = extraerCategoria(mensaje);
        String marca = extraerMarca(mensaje);
        StringBuilder sb = new StringBuilder("😔 Lo siento, no encontré productos que coincidan.\n\n");
        if (!categoria.isEmpty() && !marca.isEmpty()) {
            sb.append("No tenemos ").append(categoria.toLowerCase()).append(" para ").append(marca).append(" en este momento.");
        } else if (!categoria.isEmpty()) {
            sb.append("No tenemos ").append(categoria.toLowerCase()).append(" disponibles en este momento.");
        } else if (!marca.isEmpty()) {
            sb.append("No tenemos productos para ").append(marca).append(" en este momento.");
        } else {
            sb.append("No encontré ningún producto relacionado con tu consulta.");
        }
        sb.append("\n\n💡 Sugerencias:\n• Prueba con otro nombre de repuesto\n• Usa una categoría (filtro, freno, aceite, etc.)\n• Especifica la marca de tu vehículo");
        return sb.toString();
    }

    private String combinarRespuestaConProductos(String respuesta, List<ProductoDTO> productos) {
        if (productos.isEmpty()) return respuesta;
        StringBuilder sb = new StringBuilder(respuesta);
        sb.append("\n\n📦 Productos relacionados:\n");
        int limit = Math.min(productos.size(), 3);
        for (int i = 0; i < limit; i++) {
            ProductoDTO p = productos.get(i);
            sb.append("• ").append(p.getNombre()).append(" (").append(p.getMarca()).append(") - $")
                    .append(String.format("%.2f", p.getPrecioVenta())).append("\n");
        }
        return sb.toString();
    }

    private String llamarGeminiConRateLimit(String mensaje) {
        try {
            rateLimiter.acquire();
            Thread.sleep(rateLimitMs);
            return llamarGemini(mensaje);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Servicio temporalmente no disponible. Inténtalo en unos segundos.";
        } finally {
            rateLimiter.release();
        }
    }

    private String llamarGemini(String mensaje) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                     + geminiModel + ":generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", construirPrompt(mensaje))))));
        requestBody.put("generationConfig", Map.of(
                "temperature", 0.7,
                "maxOutputTokens", 800));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return extraerTextoDeRespuesta(response.getBody());
        } catch (Exception e) {
            log.error("Error llamando a Gemini: {}", e.getMessage());
            return "Lo siento, no puedo procesar tu consulta en este momento. Por favor, reformula o intenta más tarde.";
        }
    }

    private String construirPrompt(String mensaje) {
        return String.format("""
                Eres un asistente amigable de una tienda de repuestos automotrices llamada Drivemaster.
                Mensaje del cliente: "%s"
                Responde útilmente en español. Si es una pregunta comparativa o pide consejo,
                da una explicación clara mencionando calidad, duración o precio.
                Si simplemente pide productos, responde de forma directa. Máximo 3 párrafos.
                """, mensaje);
    }

    @SuppressWarnings("unchecked")
    private String extraerTextoDeRespuesta(Map responseBody) {
        try {
            if (responseBody == null) return "No se recibió respuesta del modelo.";
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates == null || candidates.isEmpty()) return "No se recibió respuesta.";
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) return "No se recibió contenido.";
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.error("Error extrayendo respuesta de Gemini: {}", e.getMessage());
            return "Lo siento, ocurrió un error al procesar la respuesta.";
        }
    }

    private ProductoDTO convertirADTO(Producto producto) {
        ProductoDTO dto = new ProductoDTO();
        dto.setId(producto.getId());
        dto.setCodigo(producto.getCodigo());
        dto.setNombre(producto.getNombre());
        dto.setCategoria(producto.getCategoria());
        dto.setMarca(producto.getMarca());
        dto.setPrecioVenta(producto.getPrecioVenta());
        dto.setStockActual(producto.getStockActual());
        return dto;
    }

    private static class CachedChatData {
        final String responseText;
        final List<ProductoDTO> products;
        final long timestamp;

        CachedChatData(String responseText, List<ProductoDTO> products) {
            this.responseText = responseText;
            this.products = Collections.unmodifiableList(new ArrayList<>(products));
            this.timestamp = System.currentTimeMillis();
        }
    }
}