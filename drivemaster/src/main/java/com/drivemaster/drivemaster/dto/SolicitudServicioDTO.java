package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Diagnostico;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista de una solicitud del marketplace. Según quién la consulta se llenan
 * datos distintos: el taller ve el contacto del cliente solo cuando la solicitud
 * es suya, y el cliente ve el contacto del taller asignado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudServicioDTO {
    private String id;
    private String estado;

    // Vehículo
    private String vehiculoId;
    private String vehiculoDescripcion;
    private String vehiculoPlaca;
    private String vehiculoImagenUrl;

    // Solicitud
    private String sintomas;
    private List<String> fotos;
    private String ciudad;
    private String barrio;
    private String preferenciaHorario;
    private LocalDateTime fechaCreacion;

    // Cliente (visible para el taller asignado y el ADMIN)
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteCorreo;

    // Taller asignado
    private String tallerId;
    private String tallerNombre;
    private String tallerDireccion;
    private String tallerTelefono;
    private Double distanciaTallerKm;
    private LocalDateTime fechaAsignacion;
    private LocalDateTime fechaLimiteRespuesta;

    // Aceptación y cita de revisión
    private Boolean aceptadaPorTaller;
    private LocalDateTime fechaRevision;
    private String mecanicoId;
    private String mecanicoNombre;

    // Diagnóstico
    private Double tarifaDiagnostico;
    private String pagoDiagnosticoEstado;
    private LocalDateTime fechaLimitePagoDiagnostico;

    private String motivoCierre;

    // Diagnóstico y repuestos elegidos (visibles para el cliente, el taller y el ADMIN)
    private Diagnostico diagnostico;
    private List<RepuestoSeleccionado> repuestos;

    // Cotización vigente (detalle en /api/cotizaciones/orden/{id})
    private String cotizacionId;

    // Etapas finales
    private String subestado;
    private LocalDateTime fechaInicioInstalacion;
    private LocalDateTime fechaLista;
    private LocalDateTime fechaEntrega;
    private Boolean recepcionConfirmada;
    private LocalDateTime fechaRecepcionCliente;
    private Integer calificacionTaller;
    private String comentarioCalificacion;

    private List<EventoOrden> eventos;
}
