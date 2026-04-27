package com.drivemaster.drivemaster.dto.reporte;

 
import lombok.Data;
 
@Data
public class VentaResumenDTO {
    private String id;
    private String fecha;
    private Double total;
    private String metodoPago;
    private String estado;
    private Integer cantidadProductos;
}