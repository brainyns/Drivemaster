package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Mecanico;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TallerDTO {
    private String id;
    private String nombre;
    private String ciudad;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private List<String> especialidades;
    // Promedio de las calificaciones de los clientes (null sin calificaciones) y cuántas son
    private Double rating;
    private Integer calificaciones;
    private Double precioManoObra;
    private Boolean activo;

    // Solo se llena cuando se calcula contra una ubicación de referencia
    private Double distanciaKm;

    // ─── Marketplace ───
    private String barrio;
    private String telefono;
    private Double tarifaHora;
    private String horario;
    private String logoUrl;

    // Solo para el dueño del taller y el ADMIN (null en el listado público)
    private String estadoAprobacion;
    private String motivoRechazo;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaAprobacion;
    // false: no responsable de IVA (su mano de obra y su diagnóstico van al 0 %)
    private Boolean responsableIva;
    private String correoUsuario;
    private List<Mecanico> mecanicos;
    private String planActual;
    private LocalDateTime planVence;
    private CuentaBancariaDTO cuentaBancaria;
}
