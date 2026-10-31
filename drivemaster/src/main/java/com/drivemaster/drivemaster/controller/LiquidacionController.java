package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.service.LiquidacionService;
import com.drivemaster.drivemaster.service.LiquidacionTablaService;
import com.drivemaster.drivemaster.service.dispersion.FirmaEventosPayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Liquidaciones a talleres/tiendas y reembolsos a clientes (solo ADMIN). */
@RestController
@RequestMapping("/api/liquidaciones")
public class LiquidacionController {

    private static final Logger log = LoggerFactory.getLogger(LiquidacionController.class);

    private final LiquidacionService service;
    private final LiquidacionTablaService tabla;
    private final FirmaEventosPayout firmaEventos;

    public LiquidacionController(LiquidacionService service, LiquidacionTablaService tabla, FirmaEventosPayout firmaEventos) {
        this.service = service;
        this.tabla = tabla;
        this.firmaEventos = firmaEventos;
    }

    /** Modo de dispersión (SIMULADO / MANUAL / WOMPI), para mostrarlo en la pantalla. */
    @GetMapping("/modo")
    public ResponseEntity<Map<String, Object>> modo() {
        return ResponseEntity.ok(service.modo());
    }

    @GetMapping("/repartos")
    public ResponseEntity<List<RepartoPago>> repartos(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.repartos(estado));
    }

    /**
     * Tabla paginada de liquidaciones: estado, desde/hasta (AAAA-MM-DD, hora de Colombia), page, size y sort
     * (fecha, monto, estado o beneficiario). Cada fila trae el nombre actual y la cuenta enmascarada, y resumen no
     * depende de los filtros. /repartos sigue devolviendo la lista completa.
     */
    @GetMapping("/repartos/tabla")
    public ResponseEntity<LiquidacionTablaService.Tabla> repartosTabla(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(tabla.tabla(estado, RangoFechas.de(desde, hasta),
                PeticionTabla.de(page, size, null, null, sort, LiquidacionService.ORDEN_REPARTOS)));
    }

    @PatchMapping("/repartos/{id}/liquidar")
    public ResponseEntity<RepartoPago> liquidar(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body,
                                                Authentication auth) {
        return ResponseEntity.ok(service.liquidar(id, auth.getName(),
                body != null ? body.get("numeroTransferencia") : null, body != null ? body.get("nota") : null));
    }

    /** Salida manual de una línea PROCESANDO sin evento de Wompi: { resultado: LIQUIDADO | FALLIDO, motivo, referencia }. */
    @PatchMapping("/repartos/{id}/resolver")
    public ResponseEntity<RepartoPago> resolver(@PathVariable Long id,
                                                @RequestBody Map<String, String> body,
                                                Authentication auth) {
        return ResponseEntity.ok(service.resolver(id, auth.getName(), body.get("resultado"), body.get("motivo"), body.get("referencia")));
    }

    @GetMapping("/reembolsos")
    public ResponseEntity<List<Reembolso>> reembolsos(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.reembolsos(estado));
    }

    @PatchMapping("/reembolsos/{id}/realizado")
    public ResponseEntity<Reembolso> realizado(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, String> body,
                                               Authentication auth) {
        return ResponseEntity.ok(service.marcarReembolsado(id, auth.getName(), body != null ? body.get("nota") : null));
    }

    /** Público para que Wompi pueda llamarlo: sin firma válida (o sin secreto configurado) no se procesa nada. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "X-Event-Checksum", required = false) String checksum) {
        if (!firmaEventos.esValida(body, checksum)) {
            log.warn("Evento de Wompi Payouts rechazado por firma inválida: {}", body.get("event"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        service.procesarWebhookPayout(body);
        return ResponseEntity.ok().build();
    }
}
