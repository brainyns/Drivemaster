package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.service.PedidoTiendaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos-tienda")
public class PedidoTiendaController {

    private final PedidoTiendaService service;

    public PedidoTiendaController(PedidoTiendaService service) {
        this.service = service;
    }

    // ─── Tienda ──────────────────────────────────────────────────────────────

    @GetMapping("/mios")
    public ResponseEntity<List<PedidoTienda>> misPedidos(Authentication auth) {
        return ResponseEntity.ok(service.misPedidos(auth.getName()));
    }

    @PatchMapping("/{id}/preparar")
    public ResponseEntity<PedidoTienda> preparar(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.iniciarPreparacion(auth.getName(), id));
    }

    @PatchMapping("/{id}/despachar")
    public ResponseEntity<PedidoTienda> despachar(@PathVariable String id,
                                                  @RequestBody(required = false) Map<String, String> body,
                                                  Authentication auth) {
        return ResponseEntity.ok(service.despachar(auth.getName(), id, body != null ? body.get("nota") : null));
    }

    @PatchMapping("/{id}/no-despachar")
    public ResponseEntity<PedidoTienda> noDespachar(@PathVariable String id,
                                                    @RequestBody(required = false) Map<String, String> body,
                                                    Authentication auth) {
        // body: { tipoMotivo: "SIN_REPUESTO" | "OTRO", detalle }
        return ResponseEntity.ok(service.noPuedoDespachar(auth.getName(), id,
                body != null ? body.get("tipoMotivo") : null, body != null ? body.get("detalle") : null));
    }

    // ─── Taller / cliente / ADMIN ────────────────────────────────────────────

    @GetMapping("/orden/{ordenId}")
    public ResponseEntity<List<PedidoTienda>> deOrden(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(service.deOrden(auth.getName(), ordenId));
    }

    @PostMapping("/orden/{ordenId}/recibidos")
    public ResponseEntity<List<PedidoTienda>> recibidos(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(service.repuestosRecibidos(auth.getName(), ordenId));
    }
}
