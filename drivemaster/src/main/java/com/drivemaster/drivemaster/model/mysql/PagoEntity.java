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

    @Column(nullable = false)
    private String solicitudId;

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
}
