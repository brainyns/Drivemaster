package com.drivemaster.drivemaster.dto.reporte;


import lombok.Data;
import java.util.List;
import java.util.Map;
 
@Data
public class ReporteProductosDTO {
    private String periodo;
    private List<ProductoReporteDTO> masVendidos;
    private List<ProductoReporteDTO> menosVendidos;
    private List<ProductoReporteDTO> sinMovimiento;
    private Map<String, Double>      ventasPorDia;   // para gráfica
    private Map<String, Long>        unidadesPorCategoria;
}
 