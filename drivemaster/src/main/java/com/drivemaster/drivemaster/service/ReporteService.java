package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.reporte.*;
 
public interface ReporteService {
 
    DashboardDTO            getDashboard();
 
    InventarioClientesDTO   getInventarioClientes();
    ReporteClientesDTO      getReportesClientes(String periodo);
    HistorialClienteDTO     getHistorialCliente(String clienteId);
 
    InventarioProductosDTO  getInventarioProductos();
    ReporteProductosDTO     getReportesProductos(String periodo);
}
 