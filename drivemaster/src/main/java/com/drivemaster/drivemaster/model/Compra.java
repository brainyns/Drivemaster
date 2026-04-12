package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "compras")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Compra {

    @Id
    private String id;

    private String proveedorId;
    private LocalDateTime fecha;

    private List<DetalleCompra> productos;

    private Double total;
    private String usuarioId;
}
