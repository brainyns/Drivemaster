package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;
import com.drivemaster.drivemaster.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final AlcanceTienda alcanceTienda;

    public ProductoController(ProductoService productoService,
                              MovimientoInventarioService movimientoInventarioService,
                              AlcanceTienda alcanceTienda) {
        this.productoService = productoService;
        this.movimientoInventarioService = movimientoInventarioService;
        this.alcanceTienda = alcanceTienda;
    }

    // LISTAR: productos activos de las tiendas visibles (público, sin datos internos)
    @GetMapping
    public List<Producto> listar() {
        return productoService.listarCatalogoPublico();
    }

    // LISTAR los productos de la tienda del usuario TIENDA
    @GetMapping("/mi-tienda")
    public List<Producto> listarMiTienda(Authentication auth) {
        return productoService.listarPorTienda(alcanceTienda.tiendaId(auth));
    }

    // OBTENER POR ID
    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtenerPorId(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(alcanceTienda.productoGestionable(auth, id));
    }

    // CREAR: una tienda siempre crea en su propio catálogo; el personal interno,
    // si no indica tienda, en la de DriveMaster.
    @PostMapping("/guardar")
    public ResponseEntity<MovimientoInventario> guardar(@RequestBody Producto producto, Authentication auth) {
        String tiendaId = alcanceTienda.tiendaId(auth);
        if (tiendaId != null) {
            producto.setTiendaId(tiendaId);
        }
        return ResponseEntity.ok(movimientoInventarioService.registrarNuevoProducto(producto, auth.getName()));
    }

    // ACTUALIZAR
    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable String id, @RequestBody Producto producto, Authentication auth) {
        alcanceTienda.productoGestionable(auth, id);
        return ResponseEntity.ok(productoService.actualizarProducto(id, producto));
    }

    // ELIMINAR
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id, Authentication auth) {
        alcanceTienda.productoGestionable(auth, id);
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }

    // SUBIR IMAGEN
    @PostMapping("/{id}/imagen")
    public ResponseEntity<Map<String, String>> subirImagen(
            @PathVariable String id,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication auth) throws IOException {
        alcanceTienda.productoGestionable(auth, id);
        String url = productoService.subirImagen(id, archivo);
        return ResponseEntity.ok(Map.of("url", url));
    }

}
