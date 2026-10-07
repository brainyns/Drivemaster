package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Dinero que hay que devolver al cliente (tienda que no despacha, pago que llegó
 * cuando la solicitud ya no lo esperaba, falta de stock al confirmar…). El
 * reembolso se hace a mano en Wompi y el ADMIN lo marca como REALIZADO.
 */
@Entity
@Table(name = "reembolsos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reembolso {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String REALIZADO = "REALIZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pagoId;

    private String ordenServicioId;
    private String pedidoTiendaId;
    // Compra directa del catálogo (concepto CATALOGO)
    private String compraCatalogoId;
    private String usuarioId;
    // Para ubicar la transacción en el panel de Wompi
    private String referenciaPago;
    private String transactionId;

    @Column(nullable = false)
    private Double monto;

    @Column(length = 500, nullable = false)
    private String motivo;

    @Column(length = 12, nullable = false)
    private String estado;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaRealizado;
    @Column(length = 500)
    private String nota;
}
