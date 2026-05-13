package com.drivemaster.drivemaster.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {
    private String respuesta;
    private List<ProductoDTO> productos;

    public ChatResponse() {
    }

    public ChatResponse(String respuesta, List<ProductoDTO> productos) {
        this.respuesta = respuesta;
        this.productos = productos;
    }
}