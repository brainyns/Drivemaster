package com.drivemaster.drivemaster.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    private String id;

    private String nombre;
    private String correo;

    @JsonIgnore
    private String password;

    private String rol; // SUPERADMIN, ADMIN, VENDEDOR
    private Boolean activo;
}
