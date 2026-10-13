package com.drivemaster.drivemaster.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.drivemaster.drivemaster.websocket.NotificationWebSocketHandler;

/**
 * Eventos del flujo anterior ("solicitud" y "venta_estado"). Solo los recibe el personal interno;
 * los avisos del flujo nuevo van por {@link NotificacionService}.
 */
@Service
public class NotificationService {

    static final List<String> ROLES_PERSONAL = List.of("SUPERADMIN", "ADMIN", "VENDEDOR");

    private final NotificationWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void notificar(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.putAll(data);
        try {
            handler.enviarARoles(ROLES_PERSONAL, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // las notificaciones nunca deben romper el flujo principal
        }
    }
}
