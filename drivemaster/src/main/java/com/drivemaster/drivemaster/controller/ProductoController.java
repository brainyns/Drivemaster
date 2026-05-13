package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;
import com.drivemaster.drivemaster.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final MovimientoInventarioService movimientoInventarioService;

    public ProductoController(ProductoService productoService,
                              MovimientoInventarioService movimientoInventarioService) {
        this.productoService = productoService;
        this.movimientoInventarioService = movimientoInventarioService;
    }

    // LISTAR
    @GetMapping
    public List<Producto> listar() {
        return productoService.listarTodos();
    }

    // OBTENER POR ID
    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    // CREAR
    @PostMapping("/guardar")
    public ResponseEntity<MovimientoInventario> guardar(@RequestBody Producto producto) {
        return ResponseEntity.ok(movimientoInventarioService.registrarNuevoProducto(producto, "admin"));
    }

    // ACTUALIZAR
    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable String id, @RequestBody Producto producto) {
        return ResponseEntity.ok(productoService.actualizarProducto(id, producto));
    }

    // ELIMINAR
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }

}