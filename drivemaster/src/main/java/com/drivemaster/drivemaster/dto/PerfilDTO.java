package com.drivemaster.drivemaster.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilDTO {
    private String id;
    private String nombre;
    private String correo;
    private String rol;
    private Instant ultimoLogin;
    private Instant fechaCreacion;
    private Map<String, Object> stats;
}
