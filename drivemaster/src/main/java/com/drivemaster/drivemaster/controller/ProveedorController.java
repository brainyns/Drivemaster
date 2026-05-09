package com.drivemaster.drivemaster.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.service.ProveedorService;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public List<Proveedor> listar() {
        return proveedorService.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Proveedor> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(proveedorService.obtenerPorId(id));
    }

    @PostMapping("/guardar")
    public ResponseEntity<Proveedor> guardar(@RequestBody Proveedor proveedor) {
        return ResponseEntity.ok(proveedorService.crear(proveedor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Proveedor> actualizar(@PathVariable String id, @RequestBody Proveedor proveedor) {
        proveedor.setId(id);
        return ResponseEntity.ok(proveedorService.crear(proveedor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        proveedorService.eliminarProveedor(id);
        return ResponseEntity.noContent().build();
    }
}