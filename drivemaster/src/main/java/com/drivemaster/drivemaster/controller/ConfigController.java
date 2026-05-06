package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.mysql.*;
import com.drivemaster.drivemaster.repository.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final MetodoPagoRepository metodoPagoRepo;
    private final EstadoVentaRepository estadoVentaRepo;
    private final ParametroRepository parametroRepo;

    // ── Métodos de pago ──────────────────────────────
    @GetMapping("/metodos-pago")
    public List<MetodoPago> listarMetodosPago() {
        return metodoPagoRepo.findByActivoTrue();
    }

    @PostMapping("/metodos-pago")
    public ResponseEntity<MetodoPago> crearMetodoPago(@RequestBody MetodoPago mp) {
        if (metodoPagoRepo.existsByCodigo(mp.getCodigo())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(metodoPagoRepo.save(mp));
    }

    @PatchMapping("/metodos-pago/{codigo}/toggle")
    public ResponseEntity<MetodoPago> toggleMetodoPago(@PathVariable String codigo) {
        return metodoPagoRepo.findByCodigo(codigo).map(mp -> {
            mp.setActivo(!mp.getActivo());
            return ResponseEntity.ok(metodoPagoRepo.save(mp));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Estados de venta ─────────────────────────────
    @GetMapping("/estados-venta")
    public List<EstadoVenta> listarEstados() {
        return estadoVentaRepo.findByActivoTrue();
    }

    // ── Parámetros ───────────────────────────────────
    @GetMapping("/parametros/{clave}")
    public ResponseEntity<Parametro> obtenerParametro(@PathVariable String clave) {
        return parametroRepo.findByClave(clave)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/parametros/{clave}")
    public ResponseEntity<Parametro> actualizarParametro(
            @PathVariable String clave,
            @RequestBody Parametro parametro) {
        return parametroRepo.findByClave(clave).map(p -> {
            p.setValor(parametro.getValor());
            p.setDescripcion(parametro.getDescripcion());
            return ResponseEntity.ok(parametroRepo.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }
}