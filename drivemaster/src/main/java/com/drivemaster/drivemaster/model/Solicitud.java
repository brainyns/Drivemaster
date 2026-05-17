package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "solicitudes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Solicitud {

    @Id
    private String id;

    private String clienteId;
    private String usuarioId;
    private List<DetalleVenta> productos = new ArrayList<>();
    private Double subtotal;
    private Double iva;
    private Double total;
    private String metodoPago;
    private String comprobanteUrl;
    private String observaciones;

    // PENDIENTE, APROBADO, RECHAZADO, COMPLETADA
    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    private String ventaId;
}
