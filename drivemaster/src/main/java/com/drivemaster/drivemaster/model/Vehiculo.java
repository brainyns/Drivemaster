package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "vehiculos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehiculo {

    @Id
    private String id;

    private String usuarioId;
    private String imagenUrl;
    private String marca;
    private String modelo;
    private Integer anio;
    private String motor;
    private String combustible;
    private String placa;
    private Integer kilometraje;

    // ─── Mantenimiento preventivo ───
    // Kilometraje al que se dio el último mantenimiento. Si es null, se toma
    // el kilometraje actual como referencia la primera vez que se evalúa.
    private Integer kmUltimoMantenimiento;

    // Cada cuántos km se recomienda mantenimiento. Por defecto 5000.
    private Integer intervaloMantenimientoKm;

    // Permite al cliente dejar de recibir correos de recordatorio (opt-out).
    private Boolean recordatoriosActivos;

    // Último correo de recordatorio enviado. Evita repetir el aviso cada día
    // una vez que el vehículo ya está vencido.
    private LocalDateTime ultimoRecordatorioEnviado;
}