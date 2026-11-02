package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO;
import com.drivemaster.drivemaster.dto.taller.MovimientosTallerDTO;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.service.InventarioTallerService;
import com.drivemaster.drivemaster.service.MovimientosTallerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** "Mi inventario" y sus movimientos. Solo el rol TALLER (SecurityConfig), y cada taller ve lo suyo: sale de la sesión. */
@RestController
@RequestMapping("/api/inventario-taller")
public class InventarioTallerController {

    private final InventarioTallerService inventario;
    private final MovimientosTallerService movimientos;

    public InventarioTallerController(InventarioTallerService inventario, MovimientosTallerService movimientos) {
        this.inventario = inventario;
        this.movimientos = movimientos;
    }

    @GetMapping
    public ResponseEntity<InventarioTallerDTO.Inventario> mio(Authentication auth) {
        return ResponseEntity.ok(inventario.inventario(auth.getName()));
    }

    @PatchMapping("/piezas/precio")
    public ResponseEntity<InventarioTallerDTO.Pieza> precio(@RequestParam String piezaId,
                                                            @RequestBody InventarioTallerDTO.CambioPrecio cuerpo, Authentication auth) {
        return ResponseEntity.ok(inventario.cambiarPrecio(auth.getName(), piezaId, cuerpo != null ? cuerpo.precioVenta() : null));
    }

    /** Mínimo para la alerta de stock bajo; { "minimo": null } la apaga. Devuelve el inventario con la pieza al día. */
    @PatchMapping("/piezas/minimo")
    public ResponseEntity<InventarioTallerDTO.Inventario> minimo(@RequestParam String piezaId,
                                                                 @RequestBody MovimientosTallerDTO.Minimo cuerpo, Authentication auth) {
        movimientos.cambiarMinimo(auth.getName(), piezaId, cuerpo != null ? cuerpo.minimo() : null);
        return ResponseEntity.ok(inventario.inventario(auth.getName()));
    }

    @PostMapping("/pedidos/{pedidoId}/llego")
    public ResponseEntity<InventarioTallerDTO.Inventario> llego(@PathVariable String pedidoId, Authentication auth) {
        return ResponseEntity.ok(inventario.confirmarLlegada(auth.getName(), pedidoId));
    }

    /** tipo: ENTRADA (entradas y reversas), SALIDA o AJUSTE; q: nombre o referencia de la pieza; desde/hasta AAAA-MM-DD. */
    @GetMapping("/movimientos")
    public MovimientosTallerDTO.Tabla tabla(@RequestParam(required = false) String tipo,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String desde,
                                            @RequestParam(required = false) String hasta,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort,
                                            Authentication auth) {
        return movimientos.tabla(auth.getName(), new MovimientosTallerService.Filtros(tipo, q, RangoFechas.de(desde, hasta)),
                PeticionTabla.de(page, size, null, null, sort, MovimientosTallerService.ORDEN));
    }

    @GetMapping("/movimientos/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "xlsx") String formato,
                                           @RequestParam(required = false) String tipo,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String desde,
                                           @RequestParam(required = false) String hasta,
                                           Authentication auth) {
        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] archivo = movimientos.exportar(auth.getName(), new MovimientosTallerService.Filtros(tipo, q, RangoFechas.de(desde, hasta)), pdf);
        String nombre = "Movimientos-mi-inventario-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo);
    }

    /** Ajuste por conteo, carga inicial, merma o devolución. No mueve dinero. */
    @PostMapping("/movimientos")
    public ResponseEntity<MovimientoPiezaTaller> registrar(@RequestBody MovimientosTallerDTO.Registro cuerpo, Authentication auth) {
        return ResponseEntity.ok(movimientos.registrar(auth.getName(), cuerpo));
    }
}
