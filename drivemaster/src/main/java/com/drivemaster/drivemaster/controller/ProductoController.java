package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;
import com.drivemaster.drivemaster.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
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

    // SUBIR IMAGEN
    @PostMapping("/{id}/imagen")
    public ResponseEntity<Map<String, String>> subirImagen(
            @PathVariable String id,
            @RequestParam("archivo") MultipartFile archivo) throws IOException {
        String url = productoService.subirImagen(id, archivo);
        return ResponseEntity.ok(Map.of("url", url));
    }

}