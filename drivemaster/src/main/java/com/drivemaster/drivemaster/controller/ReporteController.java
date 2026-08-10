package com.drivemaster.drivemaster.controller;



import com.drivemaster.drivemaster.dto.reporte.*;
import com.drivemaster.drivemaster.service.ReporteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    // ── DASHBOARD GENERAL ────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> getDashboard() {
        return ResponseEntity.ok(reporteService.getDashboard());
    }

    // ── CLIENTES ─────────────────────────────────────────────────────────────
    @GetMapping("/clientes/inventario")
    public ResponseEntity<InventarioClientesDTO> getInventarioClientes() {
        return ResponseEntity.ok(reporteService.getInventarioClientes());
    }

    @GetMapping("/clientes/reportes")
    public ResponseEntity<ReporteClientesDTO> getReportesClientes(
            @RequestParam(defaultValue = "mes") String periodo) {
        return ResponseEntity.ok(reporteService.getReportesClientes(periodo));
    }

    @GetMapping("/clientes/{clienteId}/historial")
    public ResponseEntity<HistorialClienteDTO> getHistorialCliente(
            @PathVariable String clienteId) {
        return ResponseEntity.ok(reporteService.getHistorialCliente(clienteId));
    }

    // ── PRODUCTOS ────────────────────────────────────────────────────────────
    @GetMapping("/productos/inventario")
    public ResponseEntity<InventarioProductosDTO> getInventarioProductos() {
        return ResponseEntity.ok(reporteService.getInventarioProductos());
    }

    @GetMapping("/productos/reportes")
    public ResponseEntity<ReporteProductosDTO> getReportesProductos(
            @RequestParam(defaultValue = "mes") String periodo) {
        return ResponseEntity.ok(reporteService.getReportesProductos(periodo));
    }
}