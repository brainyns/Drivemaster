package com.drivemaster.drivemaster.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmailServiceImpl implements EmailsService {

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name:DriveMaster}")
    private String senderName;

    public EmailServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void enviarEmail(String destinatario, String asunto, String contenido) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asunto,
                "htmlContent", contenido);
        enviar(body);
    }

    @Override
    public void enviarEmailConAdjunto(String destinatario, String asunto, String html,
                                       byte[] adjunto, String nombreAdjunto) {
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
        enviar(body);
    }

    private void enviar(Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(BREVO_URL, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Error al enviar el correo: " + e.getMessage(), e);
        }
    }
}
