package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
 
@Data
public class ProductoReporteDTO {
    private String  id;
    private String  codigo;
    private String  nombre;
    private String  categoria;
    private String  marca;
    private Integer stockActual;
    private Integer stockMinimo;
    private Double  precioVenta;
    private String  estadoStock;   // NORMAL, BAJO, CRITICO, AGOTADO
    private Long    unidadesVendidas;
    private Double  totalGenerado;
}