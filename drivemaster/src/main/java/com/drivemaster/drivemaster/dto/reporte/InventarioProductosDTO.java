package com.drivemaster.drivemaster.dto.reporte;

import lombok.Data;
import java.util.List;
 
@Data
public class InventarioProductosDTO {
    private Long totalProductos;
    private Long productosAgotados;
    private Long productosBajoStock;
    private Double valorTotalInventario;
    private List<ProductoReporteDTO> productos;
}
 