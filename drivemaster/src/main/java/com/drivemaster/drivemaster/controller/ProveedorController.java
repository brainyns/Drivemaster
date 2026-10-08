package com.drivemaster.drivemaster.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.ProveedorService;
import com.drivemaster.drivemaster.util.PdfProveedorBuilder;

/**
 * Proveedores de la TIENDA (solo ese rol: lo define SecurityConfig). Cada tienda ve y gestiona únicamente los
 * suyos; el dueño se resuelve en el servidor a partir de la sesión.
 */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;
    private final AlcanceTienda alcanceTienda;

    public ProveedorController(ProveedorService proveedorService, AlcanceTienda alcanceTienda) {
        this.proveedorService = proveedorService;
        this.alcanceTienda = alcanceTienda;
    }

    @GetMapping
    public List<Proveedor> listar(Authentication auth) {
        return proveedorService.listarDeTienda(alcanceTienda.tienda(auth).getId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Proveedor> obtenerPorId(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(proveedorService.obtenerDeTienda(id, alcanceTienda.tienda(auth).getId()));
    }

    @PostMapping("/guardar")
    public ResponseEntity<Proveedor> guardar(@RequestBody Proveedor proveedor, Authentication auth) {
        return ResponseEntity.ok(proveedorService.crearEnTienda(proveedor, alcanceTienda.tienda(auth).getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Proveedor> actualizar(@PathVariable String id, @RequestBody Proveedor proveedor, Authentication auth) {
        return ResponseEntity.ok(proveedorService.actualizarEnTienda(id, proveedor, alcanceTienda.tienda(auth).getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id, Authentication auth) {
        proveedorService.eliminarDeTienda(id, alcanceTienda.tienda(auth).getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(Authentication auth) {
        List<Proveedor> lista = proveedorService.listarDeTienda(alcanceTienda.tienda(auth).getId());
        byte[] pdf;
        try {
            pdf = PdfProveedorBuilder.construir(lista);
        } catch (Exception e) {
            throw new RuntimeException("Error al exportar proveedores a PDF", e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Proveedores.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
