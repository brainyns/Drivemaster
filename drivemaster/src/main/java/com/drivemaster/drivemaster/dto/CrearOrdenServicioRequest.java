package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearOrdenServicioRequest {
    private String vehiculoId;
    private String productoId;
    private String tallerId;
}
