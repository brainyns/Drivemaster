package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.ConfigMantenimientoRequest;
import com.drivemaster.drivemaster.dto.CrearVehiculoRequest;
import com.drivemaster.drivemaster.dto.RepuestoCompatibleDTO;
import com.drivemaster.drivemaster.dto.VehiculoDTO;
import com.drivemaster.drivemaster.service.MantenimientoService;
import com.drivemaster.drivemaster.service.OrdenServicioService;
import com.drivemaster.drivemaster.service.VehiculoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehiculos")
public class VehiculoController {

    private final VehiculoService vehiculoService;
    private final OrdenServicioService ordenServicioService;
    private final MantenimientoService mantenimientoService;

    public VehiculoController(VehiculoService vehiculoService,
                              OrdenServicioService ordenServicioService,
                              MantenimientoService mantenimientoService) {
        this.vehiculoService = vehiculoService;
        this.ordenServicioService = ordenServicioService;
        this.mantenimientoService = mantenimientoService;
    }

    @PostMapping
    public ResponseEntity<VehiculoDTO> crear(@RequestBody CrearVehiculoRequest request, Authentication auth) {
        return ResponseEntity.ok(vehiculoService.crear(auth.getName(), request));
    }

    @GetMapping("/mis-vehiculos")
    public ResponseEntity<List<VehiculoDTO>> misVehiculos(Authentication auth) {
        return ResponseEntity.ok(vehiculoService.listarPorUsuario(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehiculoDTO> obtener(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(vehiculoService.obtenerDeUsuario(auth.getName(), id));
    }

    @GetMapping("/{id}/repuestos-compatibles")
    public ResponseEntity<List<RepuestoCompatibleDTO>> repuestosCompatibles(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(vehiculoService.buscarRepuestosCompatibles(auth.getName(), id));
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<?> historial(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ordenServicioService.historialPorVehiculo(auth.getName(), id));
    }

    @PostMapping("/{id}/imagen")
public ResponseEntity<Map<String, String>> subirImagen(
        @PathVariable String id,
        @RequestParam("archivo") MultipartFile archivo,
        Authentication auth) throws IOException {
    String url = vehiculoService.subirImagen(auth.getName(), id, archivo);
    return ResponseEntity.ok(Map.of("url", url));
}

    /** Ajusta el intervalo de mantenimiento y/o activa o desactiva los recordatorios. */
    @PatchMapping("/{id}/mantenimiento")
    public ResponseEntity<VehiculoDTO> configMantenimiento(
            @PathVariable String id,
            @RequestBody ConfigMantenimientoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(vehiculoService.configurarMantenimiento(auth.getName(), id, request));
    }

    /** Dispara el barrido de recordatorios manualmente (útil para pruebas). */
    @PostMapping("/mantenimiento/enviar-recordatorios")
    public ResponseEntity<Map<String, Integer>> enviarRecordatorios() {
        return ResponseEntity.ok(Map.of("enviados", mantenimientoService.enviarRecordatoriosPendientes()));
    }
}
