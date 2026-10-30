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
    // NIT del registro (desde que se valida que no se repita); los talleres anteriores no lo tienen
    private String nit;
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

    // Responsable de IVA: su mano de obra y su diagnóstico llevan el parámetro IVA; false = 0 %. null (talleres
    // anteriores al campo) cuenta como responsable. Los repuestos siempre llevan el IVA global. Ver IvaTaller.
    private Boolean responsableIva;

    /**
     * Horario de atención en texto (ej. "Lun a Vie 8:00 a. m.–6:00 p. m.; Sáb 8:00 a. m.–1:00 p. m.").
     *
     * @deprecated El horario de verdad es la colección horarios_taller (HorarioTaller). Este texto lo regenera
     * HorarioTallerService al guardar el horario estructurado y lo sigue leyendo el cliente (mapa y lista de talleres).
     * Los talleres que no han configurado el horario estructurado conservan aquí el texto libre que escribieron.
     * TODO: retirarlo cuando el cliente lea HorarioTaller y todos los talleres tengan su horario estructurado.
     */
    @Deprecated
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

    // Cuenta bancaria para dispersiones automáticas (Wompi) y transferencias
    private CuentaBancaria cuentaBancaria;
}
