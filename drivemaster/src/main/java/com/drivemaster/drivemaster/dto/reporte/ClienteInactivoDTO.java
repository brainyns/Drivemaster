package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
 
@Data
public class ClienteInactivoDTO {
    private String id;
    private String nombre;
    private String ultimaCompra;
    private Long   diasSinComprar;
}