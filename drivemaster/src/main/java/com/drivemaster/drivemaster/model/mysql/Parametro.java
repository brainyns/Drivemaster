package com.drivemaster.drivemaster.model.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parametros")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Parametro {

    @Id
    @Column(length = 50)
    private String clave;        // IVA, TERMINAL_DEFAULT

    @Column(nullable = false, length = 100)
    private String valor;        // 16, POS-01

    @Column(length = 200)
    private String descripcion;
}