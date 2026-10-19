package com.drivemaster.drivemaster.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distancia y tiempo en carro con OSRM (servicio "table": un origen contra varios destinos en una sola petición).
 * Es un refinamiento opcional: si OSRM no responde, tarda o se le pidió hace menos de un segundo, devuelve vacío
 * y quien llama se queda con la distancia en línea recta. Por defecto usa el servidor público de demostración,
 * que pide como máximo una petición por segundo y un User-Agent identificable; OSRM_URL permite un servidor propio.
 */
@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    static final int CONNECT_TIMEOUT_MS = 3_000;
    static final int READ_TIMEOUT_MS = 5_000;
    static final long INTERVALO_MIN_MS = 1_000;

    /** Tramo en carro hasta un destino. */
    public record Tramo(double distanciaKm, double duracionMin) {
    }

    private final RestTemplate http;
    private final String baseUrl;
    private final String userAgent;
    private final AtomicLong ultimaPeticion = new AtomicLong();

    @Autowired
    public OsrmClient(@Value("${osrm.url:https://router.project-osrm.org}") String baseUrl,
                      @Value("${frontend.url:}") String frontendUrl) {
        this(crearRestTemplate(), baseUrl, frontendUrl);
    }

    OsrmClient(RestTemplate http, String baseUrl, String frontendUrl) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.userAgent = "DriveMaster/1.0" + (frontendUrl == null || frontendUrl.isBlank() ? "" : " (+" + frontendUrl + ")");
    }

    private static RestTemplate crearRestTemplate() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONNECT_TIMEOUT_MS);
        fabrica.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(fabrica);
    }

    /**
     * Tramos desde el origen hasta cada destino ({lat, lng}), en el mismo orden. Un destino sin ruta queda en null.
     * Lista vacía si no se pudo consultar.
     */
    public List<Tramo> desde(double latitud, double longitud, List<double[]> destinos) {
        if (destinos.isEmpty() || !tomarTurno()) return List.of();

        StringBuilder puntos = new StringBuilder(punto(latitud, longitud));
        for (double[] d : destinos) puntos.append(';').append(punto(d[0], d[1]));
        URI uri = URI.create(baseUrl + "/table/v1/driving/" + puntos + "?sources=0&annotations=duration,distance");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        try {
            JsonNode cuerpo = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
            return leerTabla(cuerpo, destinos.size());
        } catch (Exception e) {
            log.info("OSRM no respondió; se usa la distancia en línea recta: {}", e.getMessage());
            return List.of();
        }
    }

    /** Respeta el límite del servidor público: una petición por segundo para toda la aplicación. */
    private boolean tomarTurno() {
        long ahora = System.currentTimeMillis();
        long previa = ultimaPeticion.get();
        return ahora - previa >= INTERVALO_MIN_MS && ultimaPeticion.compareAndSet(previa, ahora);
    }

    private static String punto(double latitud, double longitud) {
        // OSRM recibe longitud,latitud
        return String.format(Locale.ROOT, "%.6f,%.6f", longitud, latitud);
    }

    /** Fila 0 de la respuesta de "table" (el origen): metros y segundos hasta cada destino. */
    static List<Tramo> leerTabla(JsonNode cuerpo, int destinos) {
        if (cuerpo == null || !"Ok".equals(cuerpo.path("code").asText())) return List.of();
        JsonNode duraciones = cuerpo.path("durations").path(0);
        JsonNode distancias = cuerpo.path("distances").path(0);
        if (duraciones.size() != destinos + 1 || distancias.size() != destinos + 1) return List.of();

        List<Tramo> tramos = new ArrayList<>(destinos);
        for (int i = 1; i <= destinos; i++) {
            JsonNode seg = duraciones.get(i);
            JsonNode metros = distancias.get(i);
            tramos.add(seg.isNumber() && metros.isNumber()
                    ? new Tramo(metros.asDouble() / 1000.0, seg.asDouble() / 60.0)
                    : null);
        }
        return Collections.unmodifiableList(tramos);
    }
}
