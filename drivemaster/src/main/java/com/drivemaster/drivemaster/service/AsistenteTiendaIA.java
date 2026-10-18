package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Tienda;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pide un texto al workflow "asistente-tienda" de n8n. Spring no tiene lógica de IA: arma el contexto (solo de la
 * tienda de la sesión) y devuelve lo que responda el modelo, con lo necesario para el panel "Detrás de escena".
 */
@Service
@Slf4j
public class AsistenteTiendaIA {

    /** texto es null si n8n falló o respondió vacío. datos: qué se le mostró al modelo, para "Detrás de escena". */
    public record Resultado(String texto, String modelo, Integer tokensEntrada, Integer tokensSalida, long ms, List<String> datos) {}

    private final ContextoAsistenteTienda contexto;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String secreto;
    private final RestTemplate restTemplate;

    public AsistenteTiendaIA(ContextoAsistenteTienda contexto, ObjectMapper objectMapper,
                             @Value("${n8n.asistente-tienda.url:http://localhost:5678/webhook/asistente-tienda}") String webhookUrl,
                             @Value("${n8n.chat.secret:}") String secreto) {
        this.contexto = contexto;
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.secreto = secreto;

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(90_000);
        this.restTemplate = new RestTemplate(fabrica);
    }

    /** Una instrucción suelta (resumen del día, titular del correo). Devuelve el texto o null. */
    public String pedir(Tienda tienda, String instruccion) {
        return conversar(tienda, instruccion, List.of()).texto();
    }

    /** Pregunta con historial (ya limpiado por quien llama) sobre los datos de la tienda. */
    public Resultado conversar(Tienda tienda, String mensaje, List<Map<String, String>> historial) {
        Map<String, Object> c = contexto.contexto(tienda);
        return llamar("tienda", mensaje, historial, c, datosMostrados(c));
    }

    /**
     * Sugerencias para el formulario de producto: el modelo recibe solo lo que escribió la tienda y la lista de
     * categorías, nada de sus ventas. Devuelve el texto crudo (JSON) para que quien llama lo valide.
     */
    public Resultado sugerirProducto(String datosProducto, List<String> categorias) {
        return llamar("producto", datosProducto, List.of(), Map.of("categorias", categorias),
                List.of("Nombre, marca y referencia que escribiste", "Lista de categorías del formulario"));
    }

    private Resultado llamar(String modo, String mensaje, List<Map<String, String>> historial, Map<String, Object> ctx,
                             List<String> datos) {
        long inicio = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (secreto != null && !secreto.isBlank()) headers.set("X-Chat-Secret", secreto);

            Map<String, Object> cuerpo = Map.of(
                    "modo", modo,
                    "mensaje", mensaje,
                    "historial", historial,
                    // Texto y no objeto: n8n lo pega tal cual en el mensaje de sistema.
                    "contexto", objectMapper.writeValueAsString(ctx));

            ResponseEntity<Map<String, Object>> r = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, new HttpEntity<>(cuerpo, headers), new ParameterizedTypeReference<>() {});
            Map<String, Object> b = r.getBody() == null ? Map.of() : r.getBody();
            Object texto = b.get("respuesta");
            return new Resultado(texto != null && !texto.toString().isBlank() ? texto.toString().trim() : null,
                    b.get("modelo") == null ? null : b.get("modelo").toString(),
                    entero(b.get("tokensEntrada")), entero(b.get("tokensSalida")),
                    System.currentTimeMillis() - inicio, datos);
        } catch (Exception e) {
            log.error("Error llamando al asistente de tienda en n8n ({}): {}", webhookUrl, e.getMessage());
            return new Resultado(null, null, null, null, System.currentTimeMillis() - inicio, datos);
        }
    }

    /** Lo que vio el modelo, en palabras de la tienda y con cantidades. */
    @SuppressWarnings("unchecked")
    static List<String> datosMostrados(Map<String, Object> c) {
        List<String> d = new ArrayList<>();
        d.add("Informes de los últimos 6 meses: ventas, más vendidos, stock bajo, despachos y liquidación");
        d.add(cuantos(c.get("reponer"), "producto por reponer", "productos por reponer"));
        d.add(cuantos(c.get("comprasRecientes"), "compra reciente a proveedores", "compras recientes a proveedores"));
        d.add(cuantos(c.get("pedidosPorDespachar"), "pedido esperando despacho", "pedidos esperando despacho"));
        d.add(cuantos(c.get("sinMovimiento90d"), "producto sin ventas en 90 días", "productos sin ventas en 90 días"));
        d.add(cuantos(c.get("masGanancia90d"), "producto en el análisis de márgenes", "productos en el análisis de márgenes"));
        return d;
    }

    private static String cuantos(Object lista, String uno, String varios) {
        int n = lista instanceof List<?> l ? l.size() : 0;
        return n + " " + (n == 1 ? uno : varios);
    }

    private static Integer entero(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
