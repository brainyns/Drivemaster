package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cuánto de un pago del marketplace le corresponde a cada taller o tienda. La
 * liquidación (transferencia) es manual: el ADMIN la marca como LIQUIDADO.
 */
@Entity
@Table(name = "repartos_pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepartoPago {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String LIQUIDADO = "LIQUIDADO";
    // La tienda no despachó: su parte se reembolsa al cliente
    public static final String ANULADO = "ANULADO";

    public static final String TALLER = "TALLER";
    public static final String TIENDA = "TIENDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pagoId;

    private String ordenServicioId;
    private String cotizacionId;
    private String pedidoTiendaId;

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
}
