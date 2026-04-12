package com.drivemaster.drivemaster.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/movimientos")
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;

    public MovimientoInventarioController(MovimientoInventarioService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @GetMapping
    public List<MovimientoInventario> listar(
            @RequestParam(required = false) String productoId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(required = false) String fechaFin) {

        LocalDateTime inicio = fechaInicio != null && !fechaInicio.isBlank()
                ? LocalDate.parse(fechaInicio).atStartOfDay() : null;
        LocalDateTime fin = fechaFin != null && !fechaFin.isBlank()
                ? LocalDate.parse(fechaFin).atTime(23, 59, 59) : null;

        return movimientoService.buscarConFiltros(productoId, tipo, inicio, fin);
    }

    @PostMapping("/registrar")
    public ResponseEntity<MovimientoInventario> registrar(
            @RequestParam String productoId,
            @RequestParam String tipo,
            @RequestParam Integer cantidad,
            @RequestParam String motivo) {
        MovimientoInventario m = movimientoService.registrarMovimiento(
                productoId, tipo, cantidad, motivo, "MANUAL", null);
        return ResponseEntity.ok(m);
    }
}