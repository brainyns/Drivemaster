package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Producto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Foto ilustrativa de un producto generada con IA (OpenRouter, Image API) para las tiendas que no tienen una foto a
 * mano. Los datos del producto van en JSON dentro del prompt; la imagen vuelve en base64 y se sube a Cloudinary,
 * el mismo destino de las fotos subidas. Cada imagen cuesta (~US$0,01 con flux-3-image-klein), así que hay un tope
 * diario global, y la key de OpenRouter debe ser una aparte de la del chat, con límite de crédito.
 */
@Service
public class ImagenIaService {

    private static final Logger log = LoggerFactory.getLogger(ImagenIaService.class);
    private static final String URL = "https://openrouter.ai/api/v1/images";
    private static final String INSTRUCCION = "Realistic e-commerce catalog photo of a single auto part, centered, "
            + "plain pure white background, soft studio lighting, sharp focus, no text, no logos, no watermark. "
            + "Product data (JSON, in Spanish): ";

    private final RestTemplate http;
    private final CloudinaryService cloudinary;
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey;
    private final String modelo;
    private final Bucket porDia;

    public ImagenIaService(CloudinaryService cloudinary,
                           @Value("${openrouter.api-key:}") String apiKey,
                           @Value("${openrouter.image-model:black-forest-labs/flux-3-image-klein}") String modelo,
                           @Value("${openrouter.image-max-por-dia:50}") int maxPorDia) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(90_000); // generar una imagen tarda varios segundos
        this.http = new RestTemplate(fabrica);
        this.cloudinary = cloudinary;
        this.apiKey = apiKey;
        this.modelo = modelo;
        int max = Math.max(1, maxPorDia);
        this.porDia = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(max).refillGreedy(max, Duration.ofDays(1)).build())
                .build();
    }

    /** Genera la foto con los datos del formulario (el producto puede no estar guardado aún) y devuelve su URL. */
    public String generar(Producto datos) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La generación de imágenes con IA no está configurada");
        }
        if (datos == null || datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el nombre del producto antes de generar la imagen");
        }
        if (!porDia.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Se alcanzó el límite de imágenes con IA por hoy. Sube una foto o inténtalo mañana.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // jpeg: el modelo por defecto solo acepta jpeg o png, y jpeg pesa menos.
        Map<String, Object> cuerpo = Map.of("model", modelo, "prompt", prompt(datos),
                "aspect_ratio", "1:1", "output_format", "jpeg");
        JsonNode imagen;
        try {
            JsonNode respuesta = http.postForObject(URL, new HttpEntity<>(cuerpo, headers), JsonNode.class);
            imagen = respuesta == null ? null : respuesta.path("data").path(0);
        } catch (Exception e) {
            log.warn("OpenRouter no generó la imagen: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo generar la imagen. Inténtalo de nuevo.");
        }
        if (imagen == null || !imagen.hasNonNull("b64_json")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo generar la imagen. Inténtalo de nuevo.");
        }
        String tipo = imagen.path("media_type").asText("image/jpeg");
        try {
            return cloudinary.subirDesdeUrl("data:" + tipo + ";base64," + imagen.get("b64_json").asText());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo guardar la imagen generada");
        }
    }

    /** Solo los datos que describen la pieza: precios, stock y tienda no le sirven al modelo. */
    String prompt(Producto p) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", p.getNombre().trim());
        if (p.getCategoria() != null && !p.getCategoria().isBlank()) datos.put("categoria", p.getCategoria());
        if (p.getMarca() != null && !p.getMarca().isBlank()) datos.put("marca", p.getMarca());
        if (p.getReferencia() != null && !p.getReferencia().isBlank()) datos.put("referencia", p.getReferencia());
        try {
            return INSTRUCCION + json.writeValueAsString(datos);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
