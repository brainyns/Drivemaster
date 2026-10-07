package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdenServicioDTO {
    private String id;

    private String vehiculoId;
    private String vehiculoDescripcion;
    private String vehiculoImagenUrl;

    private String productoId;
    private String productoNombre;
    private String productoMarca;

    private String tallerId;
    private String tallerNombre;

    private Double precioRepuesto;
    private Double precioManoObra;
    private Double total;

    private String estado;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPago;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFinalizacion;

    private String observaciones;

    // Cita agendada: fecha y hora de ingreso al taller.
    private LocalDateTime fechaProgramada;
    private Boolean citaNotificada;

    /**
     * La orden lleva varios días sin avanzar en su etapa: cotizada sin pagar,
     * pagada sin iniciar, o en proceso sin finalizar. Lo marca el backend con
     * las umbrales de {@link #estaRetrasada(String, LocalDateTime, LocalDateTime, LocalDateTime)}.
     */
    private boolean retrasada;

    // Pago reportado por el cliente y pendiente de confirmación del taller.
    private String metodoPago;
    private String referenciaPago;
    private LocalDateTime fechaPagoReportado;

    /**
     * Umbrales (en días) para considerar que una orden se quedó quieta.
     * El taller y el reporte la marcan como retrasada para darle prioridad.
     */
    private static final int DIAS_SIN_PAGAR = 3;
    private static final int DIAS_SIN_INICIAR = 2;
    private static final int DIAS_SIN_FINALIZAR = 5;

    public static boolean estaRetrasada(String estado,
                                        LocalDateTime fechaCreacion,
                                        LocalDateTime fechaPago,
                                        LocalDateTime fechaInicio) {
        LocalDateTime ahora = LocalDateTime.now();
        if ("COTIZADA".equals(estado)) {
            return fechaCreacion != null && fechaCreacion.isBefore(ahora.minusDays(DIAS_SIN_PAGAR));
        }
        if ("PAGADA".equals(estado)) {
            LocalDateTime base = fechaPago != null ? fechaPago : fechaCreacion;
            return base != null && base.isBefore(ahora.minusDays(DIAS_SIN_INICIAR));
        }
        if ("EN_PROCESO".equals(estado)) {
            return fechaInicio != null && fechaInicio.isBefore(ahora.minusDays(DIAS_SIN_FINALIZAR));
        }
        return false;
    }
}