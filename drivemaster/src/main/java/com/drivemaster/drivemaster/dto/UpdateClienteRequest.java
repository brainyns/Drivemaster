package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClienteRequest {
    private String identificacion;
    private String telefono;
    private String direccion;
    private String ciudad;
    private String region;
    private String referencia;
}
