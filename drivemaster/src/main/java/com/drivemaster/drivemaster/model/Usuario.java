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

    // Ningún endpoint devuelve esta entidad (usan DTO: PerfilClienteResponse, UsuarioAdminDTO, ClienteDTO...).
    // Por si alguna vez se serializa, la contraseña y los datos de seguridad no salen en el JSON. No afecta a
    // MongoDB, que guarda la entidad sin pasar por Jackson; ningún endpoint la recibe en el cuerpo.
    @JsonIgnore
    private String password;

    @JsonIgnore
    private String googleId;

    private String proveedor;

    private String identificacion;
    private String telefono;
    private String direccion;
    private String ciudad;
    private String region;
    private String referencia;
    private Boolean datosCompletos;

    private String rol;
    private List<String> permisos;
    private Boolean activo;
    private Instant fechaCreacion;
    private Instant ultimoLogin;
    private Instant fechaExpiracion;
    @JsonIgnore
    private Integer intentosFallidos;
    @JsonIgnore
    private Boolean bloqueado;
    @JsonIgnore
    private Instant fechaBloqueo;

    public boolean tienePermiso(String permiso) {
        return permisos != null && permisos.contains(permiso);
    }

    public boolean esRol(String rolBuscado) {
        return rol != null && rol.equals(rolBuscado);
    }
}
