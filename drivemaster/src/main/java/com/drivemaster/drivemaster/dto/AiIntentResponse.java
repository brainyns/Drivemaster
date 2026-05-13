package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiIntentResponse {
    private String intent;
    private String categoria;
    private String marca;
    private String modelo;
    private String nombreProducto;
}