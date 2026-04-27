package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
import java.util.List;
import java.util.Map;
 
@Data
public class ReporteClientesDTO {
    private String periodo;
    private Double totalRecaudado;
    private String clienteMasComproHoy;
    private String clienteMasComproSemana;
    private List<ClienteReporteDTO> rankingClientes;    // top por gasto
    private List<ClienteInactivoDTO> clientesInactivos;
    private Map<String, Double> ventasPorDia;           // para gráfica
}
 