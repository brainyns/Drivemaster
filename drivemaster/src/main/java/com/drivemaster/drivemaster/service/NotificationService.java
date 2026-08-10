package com.drivemaster.drivemaster.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.drivemaster.drivemaster.websocket.NotificationWebSocketHandler;

@Service
public class NotificationService {

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
            handler.broadcast(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // las notificaciones nunca deben romper el flujo principal
        }
    }
}
