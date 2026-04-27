package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
import java.util.List;
 
@Data
public class HistorialClienteDTO {
    private String clienteId;
    private String nombre;
    private List<VentaResumenDTO> compras;
}