package com.drivemaster.drivemaster.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Aviso de la campanita. Cada aviso es de un solo usuario (los avisos a un rol se copian a cada cuenta),
 * así cada quien marca sus leídas. El texto nunca lleva códigos de retiro ni enlaces con token.
 */
@Document(collection = "notificaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    public static final String SOLICITUD_NUEVA = "SOLICITUD_NUEVA";
    public static final String SOLICITUD_ASIGNADA = "SOLICITUD_ASIGNADA";
    public static final String SOLICITUD_RETIRADA = "SOLICITUD_RETIRADA";
    public static final String SOLICITUD_CANCELADA = "SOLICITUD_CANCELADA";
    public static final String SIN_TALLER = "SIN_TALLER";
    public static final String CITA_REVISION = "CITA_REVISION";
    public static final String COTIZACION_EMITIDA = "COTIZACION_EMITIDA";
    public static final String COTIZACION_RESPONDIDA = "COTIZACION_RESPONDIDA";
    public static final String COTIZACION_VENCIDA = "COTIZACION_VENCIDA";
    public static final String PAGO_APROBADO = "PAGO_APROBADO";
    public static final String PAGO_NO_APLICADO = "PAGO_NO_APLICADO";
    public static final String PEDIDO_NUEVO = "PEDIDO_NUEVO";
    public static final String PEDIDO_NO_DESPACHADO = "PEDIDO_NO_DESPACHADO";
    public static final String PEDIDO_ENVIADO = "PEDIDO_ENVIADO";
    public static final String PEDIDO_LISTO_RETIRO = "PEDIDO_LISTO_RETIRO";
    public static final String RECORDATORIO_RETIRO = "RECORDATORIO_RETIRO";
    public static final String RETIRO_CANCELADO = "RETIRO_CANCELADO";
    public static final String CODIGO_RETIRO = "CODIGO_RETIRO";
    public static final String PROBLEMA_ENVIO = "PROBLEMA_ENVIO";
    public static final String PROBLEMA_ENVIO_RESUELTO = "PROBLEMA_ENVIO_RESUELTO";
    public static final String VEHICULO_LISTO = "VEHICULO_LISTO";
    public static final String ALERTA_ADMIN = "ALERTA_ADMIN";

    @Id
    private String id;
    /** Dueño del aviso: solo él lo ve y lo marca como leído. */
    @JsonIgnore
    private String usuarioId;
    private String tipo;
    private String titulo;
    private String mensaje;
    /** Página del frontend a la que lleva el aviso (ej. "taller-solicitudes"); puede ser null. */
    private String enlace;
    /** Id de la solicitud, cotización o pedido relacionado; puede ser null. */
    private String referenciaId;
    private boolean leida;
    private Instant fechaCreacion;
    private Instant fechaLectura;
}
