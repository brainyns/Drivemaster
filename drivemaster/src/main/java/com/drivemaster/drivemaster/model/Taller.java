package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "talleres")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Taller {

    @Id
    private String id;

    // Usuario (rol TALLER) que inicia sesión y gestiona este taller
    private String usuarioId;

    private String nombre;
    private String ciudad;
    private String direccion;
    private Double latitud;
    private Double longitud;

    @Builder.Default
    private List<String> especialidades = new ArrayList<>();

    private Double rating;
    private Double precioManoObra;
    private Boolean activo;
}
