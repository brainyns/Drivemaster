package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "movimientos_inventario")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoInventario {

    @Id
    private String id;

    private String productoId;
    private String tipo; // ENTRADA, SALIDA, AJUSTE
    private Integer cantidad;

    private Integer stockAnterior;
    private Integer stockNuevo;

    private String motivo;
    private String referencia;

    private LocalDateTime fecha;

    private String usuarioId;
}

