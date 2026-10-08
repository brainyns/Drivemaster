package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cuánto de un pago del marketplace le corresponde a cada taller o tienda. La
 * liquidación depende del modo de dispersión (SIMULADO: automática; MANUAL: la paga el ADMIN).
 */
@Entity
@Table(name = "repartos_pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepartoPago {

    // Esperando el evento que habilita el pago (recepción confirmada, orden ENTREGADA...)
    public static final String PENDIENTE = "PENDIENTE";
    // El evento ocurrió: falta pagarla. Máx. 12 caracteres (columna estado varchar(12))
    public static final String POR_LIQUIDAR = "POR_LIQUIDAR";
    // Tomada por una liquidación en curso (evita que dos la paguen a la vez)
    public static final String PROCESANDO = "PROCESANDO";
    // La dispersión falló: el ADMIN puede reintentar
    public static final String FALLIDO = "FALLIDO";
    public static final String LIQUIDADO = "LIQUIDADO";
    // La tienda no despachó: su parte se reembolsa al cliente
    public static final String ANULADO = "ANULADO";

    // Línea de la comisión de la plataforma: solo deja constancia, no se liquida (ver ComisionPlataforma)
    public static final String INFORMATIVA = "INFORMATIVA";

    public static final String PLATAFORMA = "PLATAFORMA";
    public static final String TALLER = "TALLER";
    public static final String TIENDA = "TIENDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pagoId;

    private String ordenServicioId;
    private String cotizacionId;
    // Pedido a la tienda (PedidoTienda): del taller o de una compra directa del catálogo
    private String pedidoTiendaId;
    // Compra directa del catálogo (concepto CATALOGO)
    private String compraCatalogoId;

    // DIAGNOSTICO o COTIZACION
    @Column(length = 20)
    private String concepto;

    @Column(length = 10, nullable = false)
    private String beneficiarioTipo;
    @Column(nullable = false)
    private String beneficiarioId;
    private String beneficiarioNombre;

    @Column(nullable = false)
    private Double monto;

    @Column(length = 12, nullable = false)
    private String estado;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLiquidacion;
    @Column(length = 500)
    private String nota;

    // true: la línea nació con la regla por evento (Fase 4-B). Las anteriores (null) se liquidan a mano como antes.
    private Boolean liquidacionPorEvento;
    private LocalDateTime fechaListo;
    @Column(length = 200)
    private String eventoListo;
    // SIMULADO o MANUAL
    @Column(length = 12)
    private String modoLiquidacion;
    @Column(length = 80)
    private String referenciaLiquidacion;
    // Correo del ADMIN o SISTEMA
    @Column(length = 120)
    private String liquidadoPor;
    @Column(length = 300)
    private String motivoFallo;
    private Integer intentosLiquidacion;
}
