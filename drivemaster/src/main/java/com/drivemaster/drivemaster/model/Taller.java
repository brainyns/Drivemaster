package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
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

    // Promedio de las calificaciones reales (1 a 5) y cuántas son; null y 0 mientras no haya ninguna.
    // Lo mantiene CalificacionTallerService: no se escribe a mano.
    private Double rating;
    private Integer calificaciones;
    private Double precioManoObra;
    private Boolean activo;

    // ─── Marketplace ───
    private String barrio;
    private String telefono;

    // Valor de la hora de mano de obra, para cotizar por horas además de por ítem.
    private Double tarifaHora;

    // Horario de atención en texto libre (ej. "L-V 8:00-18:00, S 8:00-13:00").
    private String horario;

    // Logo o foto del local (URL de Cloudinary); lo sube el dueño desde Mi taller o el administrador.
    private String logoUrl;

    // PENDIENTE, APROBADO, RECHAZADO (ver EstadoAprobacion). Null = taller anterior al marketplace.
    private String estadoAprobacion;
    private String motivoRechazo;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaAprobacion;

    @Builder.Default
    private List<Mecanico> mecanicos = new ArrayList<>();
}
