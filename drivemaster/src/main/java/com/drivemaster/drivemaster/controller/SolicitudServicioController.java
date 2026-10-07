package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.AceptarSolicitudRequest;
import com.drivemaster.drivemaster.dto.ConfirmarRecepcionRequest;
import com.drivemaster.drivemaster.dto.CrearSolicitudServicioRequest;
import com.drivemaster.drivemaster.dto.DiagnosticoRequest;
import com.drivemaster.drivemaster.dto.SeleccionRepuestosRequest;
import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import com.drivemaster.drivemaster.service.SolicitudServicioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Solicitudes de diagnóstico del marketplace. No confundir con /api/solicitudes,
 * que son los pedidos de productos por encargo.
 */
@RestController
@RequestMapping("/api/solicitudes-servicio")
public class SolicitudServicioController {

    private final SolicitudServicioService service;

    public SolicitudServicioController(SolicitudServicioService service) {
        this.service = service;
    }

    // ─── Cliente ─────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<SolicitudServicioDTO> crear(@RequestBody CrearSolicitudServicioRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(auth.getName(), request));
    }

    // Se sube cada foto antes de enviar la solicitud; la URL va en "fotos".
    @PostMapping("/fotos")
    public ResponseEntity<Map<String, String>> subirFoto(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(Map.of("url", service.subirFoto(archivo)));
    }

    @GetMapping("/mias")
    public ResponseEntity<List<SolicitudServicioDTO>> misSolicitudes(Authentication auth) {
        return ResponseEntity.ok(service.misSolicitudes(auth.getName()));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<SolicitudServicioDTO> cancelar(@PathVariable String id,
                                                         @RequestBody(required = false) Map<String, String> body,
                                                         Authentication auth) {
        return ResponseEntity.ok(service.cancelarPorCliente(auth.getName(), id, texto(body, "motivo")));
    }

    // ─── Taller ──────────────────────────────────────────────

    @GetMapping("/taller")
    public ResponseEntity<List<SolicitudServicioDTO>> bandeja(Authentication auth) {
        return ResponseEntity.ok(service.bandejaTaller(auth.getName()));
    }

    @PatchMapping("/{id}/aceptar")
    public ResponseEntity<SolicitudServicioDTO> aceptar(@PathVariable String id,
                                                        @RequestBody AceptarSolicitudRequest request,
                                                        Authentication auth) {
        return ResponseEntity.ok(service.aceptar(auth.getName(), id, request));
    }

    @PatchMapping("/{id}/rechazar")
    public ResponseEntity<SolicitudServicioDTO> rechazar(@PathVariable String id,
                                                         @RequestBody(required = false) Map<String, String> body,
                                                         Authentication auth) {
        return ResponseEntity.ok(service.rechazarPorTaller(auth.getName(), id, texto(body, "motivo")));
    }

    @PatchMapping("/{id}/mecanico")
    public ResponseEntity<SolicitudServicioDTO> asignarMecanico(@PathVariable String id,
                                                                @RequestBody Map<String, String> body,
                                                                Authentication auth) {
        return ResponseEntity.ok(service.asignarMecanico(auth.getName(), id, texto(body, "mecanicoId")));
    }

    @PutMapping("/{id}/diagnostico")
    public ResponseEntity<SolicitudServicioDTO> registrarDiagnostico(@PathVariable String id,
                                                                     @RequestBody DiagnosticoRequest request,
                                                                     Authentication auth) {
        return ResponseEntity.ok(service.registrarDiagnostico(auth.getName(), id, request));
    }

    // Reemplaza la lista completa de repuestos elegidos en el buscador.
    @PutMapping("/{id}/repuestos")
    public ResponseEntity<SolicitudServicioDTO> seleccionarRepuestos(@PathVariable String id,
                                                                     @RequestBody SeleccionRepuestosRequest request,
                                                                     Authentication auth) {
        return ResponseEntity.ok(service.seleccionarRepuestos(auth.getName(), id, request));
    }

    // ─── Etapas finales (taller asignado) ──────────────────────

    @PatchMapping("/{id}/iniciar-instalacion")
    public ResponseEntity<SolicitudServicioDTO> iniciarInstalacion(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.iniciarInstalacion(auth.getName(), id));
    }

    @PatchMapping("/{id}/en-pruebas")
    public ResponseEntity<SolicitudServicioDTO> enPruebas(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.marcarEnPruebas(auth.getName(), id));
    }

    @PatchMapping("/{id}/lista")
    public ResponseEntity<SolicitudServicioDTO> lista(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.marcarLista(auth.getName(), id));
    }

    @PatchMapping("/{id}/entregada")
    public ResponseEntity<SolicitudServicioDTO> entregada(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.marcarEntregada(auth.getName(), id));
    }

    // ─── Cliente: recepción ─────────────────────────────────

    @PatchMapping("/{id}/confirmar-recepcion")
    public ResponseEntity<SolicitudServicioDTO> confirmarRecepcion(@PathVariable String id,
                                                                   @RequestBody(required = false) ConfirmarRecepcionRequest request,
                                                                   Authentication auth) {
        ConfirmarRecepcionRequest r = request != null ? request : new ConfirmarRecepcionRequest();
        return ResponseEntity.ok(service.confirmarRecepcion(auth.getName(), id, r.getCalificacion(), r.getComentario()));
    }

    // ─── ADMIN ───────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<SolicitudServicioDTO>> listar(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.listarAdmin(estado));
    }

    @PatchMapping("/{id}/reasignar")
    public ResponseEntity<SolicitudServicioDTO> reasignar(@PathVariable String id,
                                                          @RequestBody(required = false) Map<String, String> body,
                                                          Authentication auth) {
        return ResponseEntity.ok(service.reasignarPorAdmin(auth.getName(), id, texto(body, "motivo")));
    }

    // ─── Común ───────────────────────────────────────────────

    // Cliente dueño, taller asignado o ADMIN (lo valida el servicio).
    @GetMapping("/{id}")
    public ResponseEntity<SolicitudServicioDTO> obtener(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.obtener(auth.getName(), id));
    }

    private static String texto(Map<String, String> body, String campo) {
        return body != null ? body.get(campo) : null;
    }
}
