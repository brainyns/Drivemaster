package com.drivemaster.drivemaster.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.service.VentaService;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @GetMapping
    public List<Venta> listar() {
        return ventaService.listarTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venta> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(ventaService.obtenerPorId(id));
    }

    @PostMapping("/guardar")
    public ResponseEntity<Venta> guardar(@RequestBody Venta venta) {
        return ResponseEntity.ok(ventaService.registrarVenta(venta));
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<Void> anular(@PathVariable String id) {
        ventaService.anularVenta(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String id) {
        byte[] pdf     = ventaService.generarPdf(id);
        String idCorto = id.substring(Math.max(0, id.length() - 8)).toUpperCase();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Factura-DriveMaster-" + idCorto + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel() {
        byte[] excel   = ventaService.exportarExcel();
        String filename = "Ventas-DriveMaster-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}