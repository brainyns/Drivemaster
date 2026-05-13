package com.drivemaster.drivemaster.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String mensaje;

    public void ChatResponse() {
    }

    public ChatRequest(String mensaje) {
        this.mensaje = mensaje;
    }
}