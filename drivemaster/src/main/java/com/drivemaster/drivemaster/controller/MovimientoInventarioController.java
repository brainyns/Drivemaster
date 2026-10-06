package com.drivemaster.drivemaster.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;
    private final AlcanceTienda alcanceTienda;

    public MovimientoInventarioController(MovimientoInventarioService movimientoService,
                                          AlcanceTienda alcanceTienda) {
        this.movimientoService = movimientoService;
        this.alcanceTienda = alcanceTienda;
    }

    // Una tienda solo ve los movimientos de su inventario.
    @GetMapping
    public List<MovimientoInventario> listar(
            @RequestParam(required = false) String productoId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(required = false) String fechaFin,
            Authentication auth) {

        LocalDateTime inicio = fechaInicio != null && !fechaInicio.isBlank()
                ? LocalDate.parse(fechaInicio).atStartOfDay()
                : null;
        LocalDateTime fin = fechaFin != null && !fechaFin.isBlank()
                ? LocalDate.parse(fechaFin).atTime(23, 59, 59)
                : null;

        return movimientoService.buscarConFiltros(productoId, tipo, inicio, fin, alcanceTienda.tiendaId(auth));
    }

    @PostMapping("/registrar")
    public ResponseEntity<MovimientoInventario> registrar(
            @RequestParam String productoId,
            @RequestParam String tipo,
            @RequestParam Integer cantidad,
            @RequestParam String motivo,
            Authentication auth) {
        alcanceTienda.productoGestionable(auth, productoId);
        MovimientoInventario m = movimientoService.registrarMovimiento(
                productoId, tipo, cantidad, motivo, "MANUAL", auth.getName());
        return ResponseEntity.ok(m);
    }
}