package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de cada vez que el ADMIN resolvió a mano una línea PROCESANDO sin evento de Wompi: quién, cuándo, qué decidió
 * y por qué. Solo se agregan filas: nunca se cambian, y un reintento no borra lo anterior, como sí pasa con la nota.
 */
@Entity
@Table(name = "resoluciones_reparto")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolucionReparto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repartoId;

    // RepartoPago.LIQUIDADO (sí salió) o RepartoPago.FALLIDO (no salió)
    @Column(length = 12, nullable = false)
    private String resultado;

    @Column(length = 500, nullable = false)
    private String motivo;

    // La de la línea al resolver, con la que se reconoce un evento tardío de Wompi; si no tenía, la que anotó el ADMIN
    @Column(length = 80)
    private String referencia;

    @Column(length = 120, nullable = false)
    private String resueltoPor;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
