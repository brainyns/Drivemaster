package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Plan activo de un taller o una tienda. Se guarda junto a los pagos (Postgres)
 * porque es información comercial. El negocio se referencia por su id de Mongo.
 */
@Entity
@Table(name = "suscripciones", indexes = @Index(name = "idx_suscripcion_actor", columnList = "actor_tipo, actor_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Suscripcion {

    public static final String ACTIVA = "ACTIVA";
    public static final String CANCELADA = "CANCELADA";
    public static final String VENCIDA = "VENCIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TALLER o TIENDA
    @Column(name = "actor_tipo", nullable = false, length = 10)
    private String actorTipo;

    @Column(name = "actor_id", nullable = false, length = 50)
    private String actorId;

    @Column(name = "plan_codigo", nullable = false, length = 20)
    private String planCodigo;

    // ACTIVA, CANCELADA, VENCIDA
    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    // null = sin vencimiento
    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    // Correo del ADMIN que la activó (o "sistema" en la migración).
    @Column(name = "activada_por", length = 120)
    private String activadaPor;
}
