package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
import java.util.List;
 
@Data
public class InventarioClientesDTO {
    private Long totalClientes;
    private Long clientesFrecuentes;
    private Long clientesOcasionales;
    private Long clientesNuevos;
    private List<ClienteReporteDTO> clientes;
}