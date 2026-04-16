package com.drivemaster.drivemaster.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.service.CompraService;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @GetMapping
    public List<Compra> listar() {
        return compraService.listarTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Compra> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(compraService.obtenerPorId(id));
    }

    @PostMapping("/guardar")
    public ResponseEntity<Compra> guardar(@RequestBody Compra compra) {
        return ResponseEntity.ok(compraService.registrarCompra(compra));
    }
}