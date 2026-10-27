package com.drivemaster.drivemaster.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String nombre;
    private String correo;
    private String password;
    private String identificacion;
    // Obligatoria y de la lista de Ciudades
    private String ciudad;
    private String rol;
    private Boolean activo;
}
