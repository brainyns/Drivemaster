package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "ventas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {

    @Id
    private String id;

    private LocalDateTime fecha;

    private String clienteId;
    private String usuarioId;

    private List<DetalleVenta> productos = new ArrayList<>();
    private List<Pago> pagos = new ArrayList<>();

    private Double total;
    private String estado; // PAGADA, ANULADA
}

