package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "estados_venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;       // PAGADA, ANULADA, PENDIENTE, CANCELADA

    @Column(nullable = false, length = 50)
    private String nombre;       // Pagada, Anulada...

    @Column(nullable = false)
    private Boolean activo = true;
}