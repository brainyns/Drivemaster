package com.drivemaster.drivemaster.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.drivemaster.drivemaster.model.DetalleVenta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudDTO {
    private String id;
    private String clienteId;
    private String clienteNombre;
    private String clienteCorreo;
    private String clienteTelefono;
    private String clienteCiudad;
    private String clienteIdentificacion;
    private List<DetalleVenta> productos;
    private Double subtotal;
    private Double iva;
    private Double total;
    private String metodoPago;
    private String observaciones;
    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String ventaId;
}
