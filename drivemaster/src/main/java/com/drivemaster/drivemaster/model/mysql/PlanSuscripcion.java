package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plan de suscripción para talleres y tiendas (PRUEBA, BASICO, PREMIUM).
 * En v1 los precios son informativos: el ADMIN activa el plan manualmente y no
 * hay cobro automático. El plan no altera el ranking de recomendación de tiendas.
 */
@Entity
@Table(name = "planes_suscripcion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanSuscripcion {

    public static final String PRUEBA = "PRUEBA";
    public static final String BASICO = "BASICO";
    public static final String PREMIUM = "PREMIUM";

    @Id
    @Column(length = 20)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    // Precio mensual en COP según el tipo de negocio.
    @Column(name = "precio_mensual_taller", nullable = false)
    private Double precioMensualTaller;

    @Column(name = "precio_mensual_tienda", nullable = false)
    private Double precioMensualTienda;

    // Duración por defecto al activarlo; null = sin vencimiento.
    @Column(name = "duracion_dias")
    private Integer duracionDias;

    @Column(nullable = false)
    private Boolean activo = true;
}
