package com.drivemaster.drivemaster.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.service.VentaService;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/ventas")
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
}