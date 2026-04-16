package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String id;
    private String nombre;
    private String correo;
    private String rol;
}
