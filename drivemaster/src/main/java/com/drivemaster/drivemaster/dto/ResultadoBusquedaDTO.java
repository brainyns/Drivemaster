package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta de la herramienta "Buscar productos" del agente del chat. Siempre trae un mensaje:
 * con una lista vacía a secas, algunos modelos terminaban sin contestarle nada al cliente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoBusquedaDTO {
    private int total;
    private List<ProductoDTO> productos;
    private String mensaje;
}
