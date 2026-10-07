package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cotización formal de una orden del marketplace. Colección propia porque una
 * orden puede tener varias versiones (el cliente pide cambios) y cada una
 * tiene su token de respuesta y su vigencia.
 *
 * Montos en COP, redondeados al peso. El IVA se calcula sobre los subtotales y el
 * anticipo (tarifa de diagnóstico ya pagada) se descuenta del total con IVA.
 * Pendiente: talleres o tiendas no responsables de IVA no deberían cobrarlo
 * (en v1 se aplica el parámetro IVA a todos).
 */
@Document(collection = "cotizaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cotizacion {

    public static final String EMITIDA = "EMITIDA";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CAMBIOS_SOLICITADOS = "CAMBIOS_SOLICITADOS";
    public static final String VENCIDA = "VENCIDA";
    public static final String REEMPLAZADA = "REEMPLAZADA";

    @Id
    private String id;

    private String ordenId;
    private Integer version;
    private String estado;

    private String tallerId;
    private String tallerNombre;
    private String usuarioId;
    private String vehiculoDescripcion;

    @Builder.Default
    private List<CotizacionItem> items = new ArrayList<>();

    // Totales (antes de IVA, IVA por tipo y total)
    private Double subtotalRepuestos;
    private Double subtotalManoObra;
    private Double subtotal;
    private Double ivaPct;
    private Double ivaRepuestos;
    private Double ivaManoObra;
    private Double iva;
    private Double total;

    // Tarifa de diagnóstico ya pagada que se descuenta (máximo: mano de obra con su IVA)
    private Double anticipoDiagnostico;
    private Double totalAPagar;

    // Comisión de la plataforma: solo informativa en v1 (no cambia ningún monto)
    private Double comisionPct;
    private Double comisionInformativa;

    private LocalDateTime vigenciaHasta;
    // Duración estimada del trabajo en el taller
    private Integer tiempoEstimadoHoras;
    // Mayor plazo de entrega de los repuestos por encargo (días)
    private Integer diasEntregaRepuestos;
    private String observaciones;

    // Token del correo: solo se guarda su hash; un solo uso
    private String tokenHash;
    private Boolean tokenUsado;

    private LocalDateTime fechaEmision;
    private String emitidaPor;
    private LocalDateTime fechaRespuesta;
    private String motivoRespuesta;
}
