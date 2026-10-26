package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // ─── Marketplace ───────────────────────────────────────────────────────
    // Las órdenes del marketplace empiezan como solicitud de diagnóstico. Null = flujo
    // anterior (V1), que conserva sus estados. En MARKETPLACE, fechaProgramada es la
    // cita de revisión.
    private String flujo;

    // Solicitud del cliente
    private String sintomas;
    @Builder.Default
    private List<String> fotos = new ArrayList<>();
    private String ciudad;
    private String barrio;
    // Dirección legible del punto que el cliente marcó en el mapa (geocodificada en el navegador)
    private String direccion;
    private Double latitud;
    private Double longitud;
    private String preferenciaHorario;

    // Asignación al taller más cercano
    @Builder.Default
    private List<String> talleresDescartados = new ArrayList<>();
    private LocalDateTime fechaAsignacion;
    private LocalDateTime fechaLimiteRespuesta;
    private Double distanciaTallerKm;

    // Taller que el cliente eligió en el mapa (null = asignación automática). Se le
    // ofrece primero; si rechaza o no responde, sigue la asignación por ciudad.
    private String tallerElegidoId;
    private String tallerElegidoNombre;

    // Aceptación del taller y cita de revisión
    private Boolean aceptadaPorTaller;
    private LocalDateTime fechaAceptacion;
    private String mecanicoId;
    private String mecanicoNombre;

    // Tarifa de diagnóstico, congelada al aceptar (ingreso del taller): lo que paga el cliente, con el IVA si lo lleva
    private Double tarifaDiagnostico;
    // Desde el IVA por taller: precio base (parámetro TARIFA_DIAGNOSTICO, igual para todos) y su IVA (el del taller,
    // 0 % si no es responsable). tarifaDiagnostico = base + IVA. Las solicitudes anteriores no los tienen: su tarifa
    // era el parámetro con el IVA ya incluido.
    private Double tarifaDiagnosticoBase;
    private Double ivaDiagnosticoPct;
    // NO_REQUERIDO, PENDIENTE, PAGADO, VENCIDO (ver EstadoOrden)
    private String pagoDiagnosticoEstado;
    private LocalDateTime fechaLimitePagoDiagnostico;

    // Motivo al cancelarse o rechazarse
    private String motivoCierre;

    // Diagnóstico del mecánico y repuestos elegidos para la cotización
    private Diagnostico diagnostico;
    @Builder.Default
    private List<RepuestoSeleccionado> repuestos = new ArrayList<>();

    // Cotización vigente (la última emitida; las anteriores quedan en la colección cotizaciones)
    private String cotizacionId;
    // Pago (tabla pagos) con el que se pagó la cotización
    private Long pagoCotizacionId;

    // ─── Etapas finales (marketplace) ───
    // Subestado opcional dentro de EN_INSTALACION (EN_PRUEBAS)
    private String subestado;
    private LocalDateTime fechaInicioInstalacion;
    private LocalDateTime fechaLista;
    private LocalDateTime fechaEntrega;
    // El cliente confirma que recibió el vehículo; la calificación (1 a 5) es opcional
    private Boolean recepcionConfirmada;
    private LocalDateTime fechaRecepcionCliente;
    private Integer calificacionTaller;
    private String comentarioCalificacion;

    @Builder.Default
    private List<EventoOrden> eventos = new ArrayList<>();
}