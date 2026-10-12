package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Petición del chat. Ahora el backend es solo un proxy hacia n8n: además del
 * mensaje, el frontend envía un sessionId estable para que el agente recuerde
 * la conversación (memoria en Postgres, dentro de n8n).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String mensaje;
    private String sessionId;
}
