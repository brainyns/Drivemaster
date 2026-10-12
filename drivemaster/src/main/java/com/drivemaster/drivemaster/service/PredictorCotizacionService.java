package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.PrediccionAprobacionDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.*;

@Service
@Slf4j
public class PredictorCotizacionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PYTHON_ML_URL = "http://127.0.0.1:8000/predict";

    // Variables del modelo para fallback en Java
    private List<Double> means = new ArrayList<>();
    private List<Double> stds = new ArrayList<>();
    private List<String> marcas = new ArrayList<>();
    private List<String> tiposServicio = new ArrayList<>();
    private List<Double> weights = new ArrayList<>();
    private Double bias = 0.0;
    private boolean fallbackCargado = false;

    @PostConstruct
    public void initFallback() {
        try {
            ClassPathResource resource = new ClassPathResource("modelo_pesos.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    root.get("means").forEach(node -> means.add(node.asDouble()));
                    root.get("stds").forEach(node -> stds.add(node.asDouble()));
                    root.get("marcas").forEach(node -> marcas.add(node.asText()));
                    root.get("tipos_servicio").forEach(node -> tiposServicio.add(node.asText()));
                    root.get("weights").forEach(node -> weights.add(node.asDouble()));
                    bias = root.get("bias").asDouble();
                    fallbackCargado = true;
                    log.info("DriveMaster Predictor: Modelo de fallback en Java cargado exitosamente.");
                }
            }
        } catch (Exception e) {
            log.warn("DriveMaster Predictor: No se pudo cargar modelo_pesos.json para fallback: {}", e.getMessage());
        }
    }

    public PrediccionAprobacionDTO predecir(Map<String, Object> params) {
        // 1. Intentar llamar al microservicio Python FastAPI
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

            Map response = restTemplate.postForObject(PYTHON_ML_URL, entity, Map.class);
            if (response != null && response.containsKey("probabilidad_aprobacion")) {
                Double probAprob = ((Number) response.get("probabilidad_aprobacion")).doubleValue();
                Double probRech = ((Number) response.get("probabilidad_rechazo")).doubleValue();
                String pred = (String) response.get("prediccion");
                String riesgo = (String) response.get("nivel_riesgo");
                List<String> recs = (List<String>) response.get("recomendaciones");

                return PrediccionAprobacionDTO.builder()
                        .probabilidadAprobacion(probAprob)
                        .probabilidadRechazo(probRech)
                        .prediccion(pred)
                        .nivelRiesgo(riesgo)
                        .recomendaciones(recs != null ? recs : List.of())
                        .origen("PYTHON_API")
                        .build();
            }
        } catch (Exception e) {
            log.debug("Servicio Python no disponible ({}), usando inferencia interna de Java.", e.getMessage());
        }

        // 2. Fallback interno en Java si Python no está encendido
        return predecirEnJava(params);
    }

    private PrediccionAprobacionDTO predecirEnJava(Map<String, Object> params) {
        if (!fallbackCargado) {
            return PrediccionAprobacionDTO.builder()
                    .probabilidadAprobacion(0.5)
                    .probabilidadRechazo(0.5)
                    .prediccion("INCIERTA")
                    .nivelRiesgo("MEDIO")
                    .recomendaciones(List.of("Modelo en inicialización."))
                    .origen("INDETERMINADO")
                    .build();
        }

        double montoRep = getDouble(params, "monto_repuestos", 0.0);
        double montoMo = getDouble(params, "monto_mano_obra", 0.0);
        double subtotal = montoRep + montoMo;
        double totalConIva = getDouble(params, "total_con_iva", Math.round(subtotal * 1.19 * 100.0) / 100.0);
        double propRep = totalConIva > 0 ? (montoRep / totalConIva) : 0.0;

        double[] rawNums = new double[]{
                getDouble(params, "anio_vehiculo", 2018.0),
                getDouble(params, "distancia_km", 5.0),
                getDouble(params, "cantidad_repuestos", 2.0),
                getDouble(params, "num_tiendas", 1.0),
                montoRep,
                montoMo,
                totalConIva,
                propRep,
                getDouble(params, "tiempo_respuesta_horas", 2.0),
                getDouble(params, "calificacion_taller", 4.6)
        };

        List<Double> xVec = new ArrayList<>();
        for (int i = 0; i < rawNums.length; i++) {
            double norm = (rawNums[i] - means.get(i)) / (stds.get(i) > 0 ? stds.get(i) : 1.0);
            xVec.add(norm);
        }

        String marcaReq = (String) params.getOrDefault("marca_vehiculo", "Chevrolet");
        for (String m : marcas) {
            xVec.add(marcaReq != null && marcaReq.equalsIgnoreCase(m) ? 1.0 : 0.0);
        }

        String servicioReq = (String) params.getOrDefault("tipo_servicio", "FRENOS");
        for (String s : tiposServicio) {
            xVec.add(servicioReq != null && servicioReq.equalsIgnoreCase(s) ? 1.0 : 0.0);
        }

        double z = bias;
        for (int i = 0; i < Math.min(xVec.size(), weights.size()); i++) {
            z += weights.get(i) * xVec.get(i);
        }

        double probAprob = Math.round(sigmoide(z) * 10000.0) / 10000.0;
        double probRechazo = Math.round((1.0 - probAprob) * 10000.0) / 10000.0;

        String nivelRiesgo;
        String prediccion;
        if (probAprob >= 0.70) {
            nivelRiesgo = "BAJO";
            prediccion = "APROBADA";
        } else if (probAprob >= 0.45) {
            nivelRiesgo = "MEDIO";
            prediccion = "INCIERTA";
        } else {
            nivelRiesgo = "ALTO";
            prediccion = "RECHAZADA";
        }

        List<String> recs = new ArrayList<>();
        if (rawNums[8] > 24.0) {
            recs.add("El tiempo de respuesta fue alto (> 24h). Enviar la cotización en las primeras 4 horas eleva la probabilidad hasta un 35%.");
        }
        if (rawNums[1] > 15.0) {
            recs.add(String.format("El cliente está a %.1f km del taller. Ofrece facilidades de cita o inspección rápida.", rawNums[1]));
        }
        if (totalConIva > 1200000.0) {
            recs.add("El monto total es elevado. Considera ofrecer repuestos alternativos o formas de pago.");
        }
        if (rawNums[9] < 4.2) {
            recs.add("La calificación del taller impacta la decisión del cliente. Promueve buenas calificaciones.");
        }
        if (probAprob >= 0.70) {
            recs.add("Cotización atractiva y con alta expectativa de éxito según el perfil del auto.");
        }

        return PrediccionAprobacionDTO.builder()
                .probabilidadAprobacion(probAprob)
                .probabilidadRechazo(probRechazo)
                .prediccion(prediccion)
                .nivelRiesgo(nivelRiesgo)
                .recomendaciones(recs)
                .origen("FALLBACK_JAVA")
                .build();
    }

    private double sigmoide(double z) {
        z = Math.max(-50.0, Math.min(50.0, z));
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }
}

