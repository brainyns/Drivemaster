package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String solicitudId;

    private String ventaId;

    @Column(nullable = false)
    private String referencia;

    @Column(nullable = false)
    private Double monto;

    private String metodo;

    @Column(nullable = false)
    private String estado;

    private String transactionId;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaPago;

    // ─── Marketplace ───
    // Qué se cobra: VENTA, SOLICITUD, DIAGNOSTICO o COTIZACION.
    // Null en los pagos anteriores al marketplace.
    @Column(length = 20)
    private String concepto;

    private String ordenServicioId;

    private String cotizacionId;

    // Usuario (cliente) que realiza el pago.
    private String usuarioId;
}
