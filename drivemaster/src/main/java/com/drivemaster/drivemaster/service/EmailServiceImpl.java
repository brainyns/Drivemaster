package com.drivemaster.drivemaster.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Correo transaccional por la API de Brevo.
 *
 * Variables de entorno: BREVO_API_KEY (clave de la API), BREVO_SENDER_EMAIL (remitente, que debe estar
 * verificado en Brevo; si falta se usa SPRING_MAIL_USERNAME), BREVO_SENDER_NAME, MAIL_DEV_LOG y MAIL_DEV_SKIP_SEND.
 *
 * Cuando Brevo rechaza el envío, el motivo queda en el log con una pista de qué revisar. Nunca se escribe
 * la clave ni el contenido del correo. Solo para desarrollo, y apagadas por defecto:
 *  - MAIL_DEV_LOG: registra el asunto y el destinatario de cada correo y SIGUE enviándolo.
 *  - MAIL_DEV_SKIP_SEND: no envía ningún correo (independiente de la anterior).
 */
@Service
public class EmailServiceImpl implements EmailsService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private static final int MAX_MENSAJE = 200;

    private final RestTemplate restTemplate;
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final boolean devLog;
    private final boolean devSkipSend;

    public EmailServiceImpl(@Qualifier("brevoRestTemplate") RestTemplate restTemplate,
                            @Value("${brevo.api.key:}") String apiKey,
                            @Value("${brevo.sender.email:}") String senderEmail,
                            @Value("${brevo.sender.name:DriveMaster}") String senderName,
                            @Value("${mail.dev-log:false}") boolean devLog,
                            @Value("${mail.dev-skip-send:false}") boolean devSkipSend) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderEmail = senderEmail != null ? senderEmail.trim() : "";
        this.senderName = senderName;
        this.devLog = devLog;
        this.devSkipSend = devSkipSend;
    }

    @PostConstruct
    void avisarConfiguracion() {
        if (devLog) {
            log.warn("MAIL_DEV_LOG activo: se registra el asunto y destinatario de cada correo. Solo para desarrollo.");
        }
        if (devSkipSend) {
            log.warn("MAIL_DEV_SKIP_SEND activo: los correos NO se envían. Solo para desarrollo.");
            return;
        }
        if (apiKey.isEmpty()) {
            log.warn("Correo: falta BREVO_API_KEY; no se enviará ningún correo hasta configurarla.");
        }
        if (senderEmail.isEmpty()) {
            log.warn("Correo: falta BREVO_SENDER_EMAIL (o SPRING_MAIL_USERNAME); Brevo rechazará los envíos sin remitente.");
        }
    }

    @Override
    public void enviarEmail(String destinatario, String asunto, String contenido) {
        if (omitirEnvio(destinatario, asunto)) return;
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asunto,
                "htmlContent", contenido);
        enviar(body, destinatario, asunto);
    }

    @Override
    public void enviarEmailConAdjunto(String destinatario, String asunto, String html,
                                       byte[] adjunto, String nombreAdjunto) {
        if (omitirEnvio(destinatario, asunto)) return;
        String base64Adjunto = Base64.getEncoder().encodeToString(adjunto);
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asunto,
                "htmlContent", html,
                "attachment", List.of(Map.of(
                        "content", base64Adjunto,
                        "name", nombreAdjunto,
                        "type", "application/pdf")));
        enviar(body, destinatario, asunto);
    }

    /** Aplica las variables de desarrollo. Devuelve true si el correo NO debe enviarse (MAIL_DEV_SKIP_SEND). */
    private boolean omitirEnvio(String destinatario, String asunto) {
        if (devLog) {
            log.info("[CORREO-DEV] Destinatario: {} | Asunto: {}{}", destinatario, asunto,
                    devSkipSend ? " | NO se envía (MAIL_DEV_SKIP_SEND)" : "");
        } else if (devSkipSend) {
            log.info("[CORREO-DEV] Envío omitido por MAIL_DEV_SKIP_SEND. Asunto: {}", asunto);
        }
        return devSkipSend;
    }

    private void enviar(Map<String, Object> body, String destinatario, String asunto) {
        if (apiKey.isEmpty()) {
            String motivo = "falta BREVO_API_KEY";
            log.error("No se pudo enviar el correo '{}' a {}: {}.", asunto, destinatario, motivo);
            throw new RuntimeException("Error al enviar el correo: " + motivo);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(BREVO_URL, request, String.class);
            log.debug("Correo '{}' enviado a {}", asunto, destinatario);
        } catch (HttpStatusCodeException e) {
            String detalle = detalleDeBrevo(e.getResponseBodyAsString());
            log.error("No se pudo enviar el correo '{}' a {}: Brevo respondió {}{}. {}", asunto, destinatario,
                    e.getStatusCode().value(), detalle.isEmpty() ? "" : " (" + detalle + ")",
                    pista(e.getStatusCode().value(), detalle));
            throw new RuntimeException("Error al enviar el correo: Brevo respondió " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            log.error("No se pudo enviar el correo '{}' a {}: sin conexión con Brevo ({}). Revisa la red del servidor.",
                    asunto, destinatario, e.getClass().getSimpleName());
            throw new RuntimeException("Error al enviar el correo: sin conexión con Brevo", e);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, e.getClass().getSimpleName());
            throw new RuntimeException("Error al enviar el correo: " + e.getClass().getSimpleName(), e);
        }
    }

    /** Código y mensaje que devuelve Brevo ({"code":"...","message":"..."}), recortados. Nunca el cuerpo completo. */
    private String detalleDeBrevo(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) return "";
        try {
            JsonNode n = json.readTree(cuerpo);
            String codigo = n.path("code").asText("");
            String mensaje = n.path("message").asText("");
            String t = (codigo.isEmpty() ? "" : codigo + ": ") + mensaje;
            return t.length() > MAX_MENSAJE ? t.substring(0, MAX_MENSAJE) + "…" : t;
        } catch (Exception ex) {
            return "";
        }
    }

    /** Qué revisar según la respuesta de Brevo. */
    static String pista(int status, String detalle) {
        String d = detalle == null ? "" : detalle.toLowerCase();
        if (status == 401 || status == 403) {
            return "Revisa que BREVO_API_KEY sea una clave de API vigente (xkeysib-…) y que la IP del servidor esté "
                    + "autorizada en Brevo (Seguridad > Direcciones IP autorizadas).";
        }
        if (d.contains("sender")) {
            return "El remitente (BREVO_SENDER_EMAIL) debe estar verificado en Brevo: Remitentes, dominios e IP > Remitentes.";
        }
        if (status == 400) {
            return "Brevo rechazó la solicitud; revisa el remitente y el destinatario.";
        }
        if (status == 429) {
            return "Se superó el límite de envíos de Brevo; reintenta más tarde.";
        }
        if (status >= 500) {
            return "Error de Brevo; reintenta más tarde.";
        }
        return "";
    }
}
