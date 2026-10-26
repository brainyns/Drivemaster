package com.drivemaster.drivemaster.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;
import com.drivemaster.drivemaster.service.ExportacionMovimientos;
import com.drivemaster.drivemaster.model.Tienda;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;
    private final AlcanceTienda alcanceTienda;
    private final ExportacionMovimientos exportacion;

    public MovimientoInventarioController(MovimientoInventarioService movimientoService,
                                          AlcanceTienda alcanceTienda,
                                          ExportacionMovimientos exportacion) {
        this.movimientoService = movimientoService;
        this.alcanceTienda = alcanceTienda;
        this.exportacion = exportacion;
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

    /**
     * Descarga en Excel (formato=xlsx) o PDF (formato=pdf) los movimientos con los filtros de la pantalla: busqueda
     * (nombre o código del producto), tipo y fechas desde/hasta (yyyy-mm-dd). Solo los de la tienda de la sesión.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Authentication auth) {
        Tienda tienda = alcanceTienda.tienda(auth);
        ExportacionMovimientos.Filtros filtros = new ExportacionMovimientos.Filtros(busqueda, tipo, fecha(desde), fecha(hasta));
        List<ExportacionMovimientos.Fila> filas = exportacion.filas(tienda.getId(), filtros);
        String nombre = "Movimientos-inventario-" + LocalDate.now();
        if ("pdf".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(exportacion.pdf(filas, tienda.getNombre(), ExportacionMovimientos.resumenFiltros(filtros)));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportacion.excel(filas));
    }

    private static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha inválida: usa el formato aaaa-mm-dd");
        }
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