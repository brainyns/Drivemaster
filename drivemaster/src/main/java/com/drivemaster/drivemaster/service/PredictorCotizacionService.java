package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.FactorExplicativoDTO;
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
    private final List<Double> means = new ArrayList<>();
    private final List<Double> stds = new ArrayList<>();
    private final List<String> marcas = new ArrayList<>();
    private final List<String> tiposServicio = new ArrayList<>();
    private final List<String> featureNames = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private Double bias = 0.0;
    private Double optimalThreshold = 0.40;
    private boolean fallbackCargado = false;

    @PostConstruct
    public void initFallback() {
        try {
            ClassPathResource resource = new ClassPathResource("modelo_pesos.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    means.clear();
                    stds.clear();
                    marcas.clear();
                    tiposServicio.clear();
                    featureNames.clear();
                    weights.clear();

                    root.get("means").forEach(node -> means.add(node.asDouble()));
                    root.get("stds").forEach(node -> stds.add(node.asDouble()));
                    root.get("marcas").forEach(node -> marcas.add(node.asText()));
                    root.get("tipos_servicio").forEach(node -> tiposServicio.add(node.asText()));
                    root.get("weights").forEach(node -> weights.add(node.asDouble()));
                    bias = root.get("bias").asDouble();

                    if (root.has("optimal_threshold")) {
                        optimalThreshold = root.get("optimal_threshold").asDouble();
                    }
                    if (root.has("feature_names")) {
                        root.get("feature_names").forEach(node -> featureNames.add(node.asText()));
                    }

                    fallbackCargado = true;
                    log.info("DriveMaster Predictor: Modelo XAI 2.0 en Java cargado exitosamente (umbral={})", optimalThreshold);
                }
            }
        } catch (Exception e) {
            log.warn("DriveMaster Predictor: No se pudo cargar modelo_pesos.json para fallback: {}", e.getMessage());
        }
    }

    public PrediccionAprobacionDTO predecir(Map<String, Object> params) {
        // 1. Intentar llamar al microservicio Python
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

            Map<?, ?> response = restTemplate.postForObject(PYTHON_ML_URL, entity, Map.class);
            if (response != null && response.containsKey("probabilidad_aprobacion")) {
                Double probAprob = ((Number) response.get("probabilidad_aprobacion")).doubleValue();
                Double probRech = ((Number) response.get("probabilidad_rechazo")).doubleValue();
                String pred = (String) response.get("prediccion");
                String riesgo = (String) response.get("nivel_riesgo");
                Double logit = response.containsKey("score_logit") ? ((Number) response.get("score_logit")).doubleValue() : null;
                Double umbral = response.containsKey("umbral_decision") ? ((Number) response.get("umbral_decision")).doubleValue() : optimalThreshold;

                List<FactorExplicativoDTO> favor = parsearFactores(response.get("factores_favor"));
                List<FactorExplicativoDTO> riesg = parsearFactores(response.get("factores_riesgo"));
                List<String> recs = (List<String>) response.get("recomendaciones");

                return PrediccionAprobacionDTO.builder()
                        .probabilidadAprobacion(probAprob)
                        .probabilidadRechazo(probRech)
                        .prediccion(pred)
                        .nivelRiesgo(riesgo)
                        .scoreLogit(logit)
                        .umbralDecision(umbral)
                        .factoresFavor(favor)
                        .factoresRiesgo(riesg)
                        .recomendaciones(recs != null ? recs : List.of())
                        .origen("PYTHON_API")
                        .build();
            }
        } catch (Exception e) {
            log.debug("Servicio Python no disponible ({}), usando inferencia interna de Java.", e.getMessage());
        }

        // 2. Fallback interno en Java con explicabilidad
        return predecirEnJava(params);
    }

    @SuppressWarnings("unchecked")
    private List<FactorExplicativoDTO> parsearFactores(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<FactorExplicativoDTO> resultado = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                resultado.add(FactorExplicativoDTO.builder()
                        .factor((String) m.get("factor"))
                        .titulo((String) m.get("titulo"))
                        .descripcion((String) m.get("descripcion"))
                        .impacto(m.get("impacto") instanceof Number ? ((Number) m.get("impacto")).doubleValue() : 0.0)
                        .tipo((String) m.get("tipo"))
                        .build());
            }
        }
        return resultado;
    }

    private PrediccionAprobacionDTO predecirEnJava(Map<String, Object> params) {
        if (!fallbackCargado) {
            return PrediccionAprobacionDTO.builder()
                    .probabilidadAprobacion(0.5)
                    .probabilidadRechazo(0.5)
                    .prediccion("INCIERTA")
                    .nivelRiesgo("MEDIO")
                    .scoreLogit(0.0)
                    .umbralDecision(optimalThreshold)
                    .factoresFavor(List.of())
                    .factoresRiesgo(List.of())
                    .recomendaciones(List.of("Modelo en inicialización."))
                    .origen("INDETERMINADO")
                    .build();
        }

        double anio = getDouble(params, "anio_vehiculo", 2018.0);
        double antiguedad = Math.max(0.0, 2026.0 - anio);
        double distKm = getDouble(params, "distancia_km", 5.0);
        double logDist = Math.log(Math.max(distKm, 0.1) + 1.0);
        double cantRep = getDouble(params, "cantidad_repuestos", 2.0);
        double numTiendas = getDouble(params, "num_tiendas", 1.0);
        double montoRep = getDouble(params, "monto_repuestos", 0.0);
        double montoMo = getDouble(params, "monto_mano_obra", 0.0);
        double subtotal = montoRep + montoMo;
        double totalConIva = getDouble(params, "total_con_iva", Math.round(subtotal * 1.19 * 100.0) / 100.0);
        double propRep = totalConIva > 0 ? (montoRep / totalConIva) : 0.0;
        double tiempoH = getDouble(params, "tiempo_respuesta_horas", 2.0);
        double logTiempo = Math.log(Math.max(tiempoH, 0.1) + 1.0);
        double calif = getDouble(params, "calificacion_taller", 4.6);
        double repDist = calif / (logDist + 0.5);
        double antigPrecio = (antiguedad / 10.0) * (totalConIva / 1000000.0);

        double[] rawNums = new double[]{
                antiguedad,
                distKm,
                logDist,
                cantRep,
                numTiendas,
                montoRep,
                montoMo,
                totalConIva,
                propRep,
                tiempoH,
                logTiempo,
                calif,
                repDist,
                antigPrecio
        };

        List<Double> xVec = new ArrayList<>();
        for (int i = 0; i < rawNums.length; i++) {
            double mean = i < means.size() ? means.get(i) : 0.0;
            double std = i < stds.size() && stds.get(i) > 1e-9 ? stds.get(i) : 1.0;
            xVec.add((rawNums[i] - mean) / std);
        }

        String marcaReq = (String) params.getOrDefault("marca_vehiculo", "Chevrolet");
        for (String m : marcas) {
            xVec.add(marcaReq != null && marcaReq.equalsIgnoreCase(m) ? 1.0 : 0.0);
        }

        String servicioReq = (String) params.getOrDefault("tipo_servicio", "MANTENIMIENTO_PREVENTIVO");
        for (String s : tiposServicio) {
            xVec.add(servicioReq != null && servicioReq.equalsIgnoreCase(s) ? 1.0 : 0.0);
        }

        double z = bias;
        List<Map.Entry<String, Double>> contribuciones = new ArrayList<>();

        for (int i = 0; i < Math.min(xVec.size(), weights.size()); i++) {
            double contrib = weights.get(i) * xVec.get(i);
            z += contrib;
            String fName = i < featureNames.size() ? featureNames.get(i) : "feat_" + i;
            contribuciones.add(new AbstractMap.SimpleEntry<>(fName, contrib));
        }

        double probAprob = Math.round(sigmoide(z) * 10000.0) / 10000.0;
        double probRechazo = Math.round((1.0 - probAprob) * 10000.0) / 10000.0;

        String nivelRiesgo;
        String prediccion;
        if (probAprob >= 0.70) {
            nivelRiesgo = "BAJO";
            prediccion = "APROBADA";
        } else if (probAprob >= optimalThreshold) {
            nivelRiesgo = "MEDIO";
            prediccion = "INCIERTA";
        } else {
            nivelRiesgo = "ALTO";
            prediccion = "RECHAZADA";
        }

        // Construir factores explicativos (XAI)
        List<FactorExplicativoDTO> factoresFavor = new ArrayList<>();
        List<FactorExplicativoDTO> factoresRiesgo = new ArrayList<>();

        contribuciones.stream()
                .filter(e -> e.getValue() > 0.05)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> factoresFavor.add(armarFactorDTO(e.getKey(), e.getValue(), true, calif, tiempoH, distKm, totalConIva, antiguedad)));

        contribuciones.stream()
                .filter(e -> e.getValue() < -0.05)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(3)
                .forEach(e -> factoresRiesgo.add(armarFactorDTO(e.getKey(), e.getValue(), false, calif, tiempoH, distKm, totalConIva, antiguedad)));

        // Recomendaciones
        List<String> recs = new ArrayList<>();
        if (tiempoH > 24.0) {
            recs.add("El tiempo de respuesta fue mayor a 24 horas. Responder en las primeras 4 horas eleva la probabilidad significativamente.");
        } else if (tiempoH > 6.0) {
            recs.add("Procura responder en menos de 3 horas para asegurar una tasa de conversión óptima.");
        }

        if (distKm > 18.0) {
            recs.add("El cliente está a " + com.drivemaster.drivemaster.util.Dinero.decimal(distKm) + " km. Ofrece facilidades de agenda o inspección rápida.");
        }

        if (antiguedad > 8 && totalConIva > 800000.0) {
            recs.add("Vehículo de más de 8 años: ofrece alternativas de repuestos homologados o facilidades de pago.");
        } else if (totalConIva > 1500000.0) {
            recs.add("Monto total elevado. Detalla las garantías de repuestos y mano de obra para dar certidumbre al cliente.");
        }

        if (calif < 4.2) {
            recs.add("Promueve que clientes satisfechos califiquen tu taller; cada 0.2 estrellas adicionales aumentan tus conversiones.");
        }

        if (probAprob >= 0.70) {
            recs.add("Cotización con excelente perfil competitivo. Lista para emitir al cliente.");
        }

        return PrediccionAprobacionDTO.builder()
                .probabilidadAprobacion(probAprob)
                .probabilidadRechazo(probRechazo)
                .prediccion(prediccion)
                .nivelRiesgo(nivelRiesgo)
                .scoreLogit(Math.round(z * 10000.0) / 10000.0)
                .umbralDecision(optimalThreshold)
                .factoresFavor(factoresFavor)
                .factoresRiesgo(factoresRiesgo)
                .recomendaciones(recs)
                .origen("FALLBACK_JAVA")
                .build();
    }

    private FactorExplicativoDTO armarFactorDTO(String feat, double contrib, boolean esFavor, double calif, double tiempo, double dist, double total, double antig) {
        double imp = Math.round(Math.abs(contrib) * 100.0) / 10.0;
        String titulo;
        String desc;

        if (feat.contains("calificacion")) {
            titulo = esFavor ? "Reputación del taller" : "Calificación del taller";
            desc = esFavor ? "La alta calificación (" + calif + " estrellas) genera gran confianza en el cliente."
                    : "La calificación (" + calif + " estrellas) reduce la confianza frente a otros talleres.";
        } else if (feat.contains("tiempo")) {
            titulo = esFavor ? "Respuesta ágil" : "Tiempo de respuesta";
            desc = esFavor ? "Respondiste en " + tiempo + " h (muy rápido). Maximiza el interés del cliente."
                    : "Respuesta en " + tiempo + " h. Enviar antes de 4 h eleva mucho la probabilidad.";
        } else if (feat.contains("antiguedad_x_precio")) {
            titulo = esFavor ? "Excelente relación costo/vehículo" : "Relación vehículo / costo";
            desc = esFavor ? "Presupuesto proporcionado para un auto con " + (int) antig + " años de antigüedad."
                    : "El valor total se percibe elevado para un vehículo con " + (int) antig + " años.";
        } else if (feat.contains("distancia")) {
            titulo = esFavor ? "Proximidad geográfica" : "Distancia geográfica";
            desc = esFavor ? "El taller está a solo " + dist + " km del cliente."
                    : "El cliente se encuentra a " + dist + " km de distancia.";
        } else if (feat.contains("total") || feat.contains("monto")) {
            titulo = esFavor ? "Precio competitivo" : "Costo del servicio";
            desc = esFavor ? "Presupuesto de " + com.drivemaster.drivemaster.util.Dinero.texto(com.drivemaster.drivemaster.util.Dinero.pesos(total)) + " COP atractivo y competitivo."
                    : "El presupuesto de " + com.drivemaster.drivemaster.util.Dinero.texto(com.drivemaster.drivemaster.util.Dinero.pesos(total)) + " COP está por encima de la media.";
        } else {
            titulo = feat.replace("_", " ");
            desc = esFavor ? "Factor que incrementa la probabilidad de aprobación." : "Factor que penaliza la probabilidad de aprobación.";
        }

        return FactorExplicativoDTO.builder()
                .factor(feat)
                .titulo(titulo)
                .descripcion(desc)
                .impacto(imp)
                .tipo(esFavor ? "FAVOR" : "RIESGO")
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
