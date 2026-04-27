package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
 
@Data
public class DashboardDTO {
    private Double totalVentasDia;
    private Double totalVentasMes;
    private Long   totalTransacciones;
    private String productoMasVendido;
    private String clienteMasImportante;
    private String metodoPagoMasUsado;
    private Double promedioTicket;
    private Double variacionSemana; // % vs semana anterior
}
