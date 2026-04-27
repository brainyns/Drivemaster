package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
 
@Data
public class ClienteReporteDTO {
    private String id;
    private String nombre;
    private String identificacion;
    private String correo;
    private String telefono;
    private Long   totalCompras;
    private Double totalGastado;
    private String ultimaCompra;
    private String metodoPagoMasUsado;
    private String clasificacion; // FRECUENTE, OCASIONAL, NUEVO
}
