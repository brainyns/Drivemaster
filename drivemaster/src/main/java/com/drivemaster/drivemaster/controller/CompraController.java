package com.drivemaster.drivemaster.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.CompraService;

/**
 * Compras a proveedores de la TIENDA (solo ese rol: lo define SecurityConfig). Cada tienda ve y registra únicamente
 * lo suyo; el dueño se resuelve en el servidor a partir de la sesión.
 */
@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;
    private final AlcanceTienda alcanceTienda;

    public CompraController(CompraService compraService, AlcanceTienda alcanceTienda) {
        this.compraService = compraService;
        this.alcanceTienda = alcanceTienda;
    }

    @GetMapping
    public List<Compra> listar(Authentication auth) {
        return compraService.listarDeTienda(alcanceTienda.tienda(auth).getId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Compra> obtenerPorId(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(compraService.obtenerDeTienda(id, alcanceTienda.tienda(auth).getId()));
    }

    @PostMapping("/guardar")
    public ResponseEntity<Compra> guardar(@RequestBody Compra compra, Authentication auth) {
        return ResponseEntity.ok(compraService.registrarCompra(compra, alcanceTienda.tienda(auth)));
    }

    /** La mercancía de una compra pendiente llegó: pasa a RECIBIDA y se suma el stock. */
    @PatchMapping("/{id}/recibir")
    public ResponseEntity<Compra> recibir(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(compraService.recibirCompra(id, alcanceTienda.tienda(auth).getId()));
    }

    /** Una compra pendiente no va a llegar: pasa a CANCELADA (motivo opcional) sin tocar el stock. */
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Compra> cancelar(@PathVariable String id, @RequestBody(required = false) Map<String, String> body,
                                           Authentication auth) {
        String motivo = body == null ? null : body.get("motivo");
        return ResponseEntity.ok(compraService.cancelarCompra(id, alcanceTienda.tienda(auth).getId(), motivo));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String id, Authentication auth) {
        byte[] pdf = compraService.generarPdfDeTienda(id, alcanceTienda.tienda(auth).getId());
        String poId = "PO-" + id.substring(Math.max(0, id.length() - 5)).toUpperCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Factura-Compra-" + poId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
