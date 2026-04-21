package com.drivemaster.drivemaster.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    private String id;

    private String nombre;
    private String correo;

    @JsonIgnore
    private String password;

    private String rol;
    private List<String> permisos;
    private Boolean activo;
    private Instant fechaCreacion;
    private Instant ultimoLogin;
    private Instant fechaExpiracion;
    private Integer intentosFallidos;
    private Boolean bloqueado;
    private Instant fechaBloqueo;

    public boolean tienePermiso(String permiso) {
        if (permisos != null && permisos.contains(permiso)) {
            return true;
        }
        return false;
    }

    public boolean esRol(String rolBuscado) {
        return rol != null && rol.equals(rolBuscado);
    }
}
