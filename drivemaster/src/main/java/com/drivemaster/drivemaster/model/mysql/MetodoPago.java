package com.drivemaster.drivemaster.model.mysql;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "metodos_pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;       // EFECTIVO, TARJETA, TRANSFERENCIA

    @Column(nullable = false, length = 50)
    private String nombre;       // Efectivo, Tarjeta, Transferencia

    @Column(length = 10)
    private String icono;        // 💵 💳 🏦

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "requiere_referencia", nullable = false)
    private Boolean requiereReferencia = false;
}
