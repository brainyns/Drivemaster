package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Orden que conecta Cliente + Vehiculo + Repuesto (Producto) + Taller.
 * Máquina de estados:
 * SOLICITADA -> COTIZADA -> PAGADA -> EN_PROCESO -> FINALIZADA
 * (y además: CANCELADA, REASIGNADA como estados secundarios)
 */
@Document(collection = "ordenes_servicio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenServicio {

    @Id
    private String id;

    private String usuarioId;
    private String vehiculoId;
    private String tallerId;
    private String productoId;

    // Foto del vehículo tomada por el cliente. Se denormaliza al crear la orden
    // para que el taller pueda "ver el carro" antes de cotizar, sin joins.
    private String vehiculoImagenUrl;

    // Datos denormalizados útiles para historial/listados sin hacer joins
    private String productoNombre;
    private String productoMarca;
    private String tallerNombre;
    private String vehiculoDescripcion; // ej: "Chevrolet Spark GT 2018"

    private Double precioRepuesto;
    private Double precioManoObra;
    private Double total;

    // Venta generada al pagar la orden (inversa de Venta.ordenServicioId)
    private String ventaId;

    // Pago reportado por el cliente (EFECTIVO / TRANSFERENCIA). La orden sigue
    // COTIZADA hasta que el taller confirma que recibió el dinero.
    private String metodoPago;
    private String referenciaPago;
    private LocalDateTime fechaPagoReportado;

    // SOLICITADA, COTIZADA, PAGADA, EN_PROCESO, FINALIZADA, CANCELADA, REASIGNADA
    private String estado;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPago;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFinalizacion;

    private String observaciones;

    // Fecha y hora en que el vehículo ingresará al taller. Se informa al cliente
    // por correo en cuanto se programa.
    private LocalDateTime fechaProgramada;

    // Evita reenviar el aviso de cita al cliente.
    private Boolean citaNotificada;
}