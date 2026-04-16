package com.drivemaster.drivemaster.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String nombre;
    private String correo;
    private String password;
    private String rol;
}
