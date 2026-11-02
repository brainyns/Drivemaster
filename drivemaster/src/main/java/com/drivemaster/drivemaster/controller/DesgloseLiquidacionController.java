package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Desglose;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO;
import com.drivemaster.drivemaster.service.DesgloseLiquidacionService;
import com.drivemaster.drivemaster.service.ResumenServicioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Venta, comisión y lo que recibe el negocio. El dueño sale de la sesión, nunca de un parámetro. */
@RestController
public class DesgloseLiquidacionController {

    private final DesgloseLiquidacionService desglose;
    private final ResumenServicioService resumen;

    public DesgloseLiquidacionController(DesgloseLiquidacionService desglose, ResumenServicioService resumen) {
        this.desglose = desglose;
        this.resumen = resumen;
    }

    @GetMapping("/api/pedidos-tienda/{id}/liquidacion")
    public ResponseEntity<Desglose> dePedido(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(desglose.dePedido(auth.getName(), id));
    }

    /** Diagnóstico y cotización de una orden del taller. */
    @GetMapping("/api/talleres/mi-taller/ordenes/{ordenId}/liquidacion")
    public ResponseEntity<Desglose> deOrden(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(desglose.deOrden(auth.getName(), ordenId));
    }

    /** El total de la orden para su taller. */
    @GetMapping("/api/talleres/mi-taller/ordenes/{ordenId}/resumen")
    public ResponseEntity<ResumenServicioDTO.Taller> resumenTaller(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(resumen.deTaller(auth.getName(), ordenId));
    }

    /** Lo que el cliente pagó por su servicio, sin comisiones. */
    @GetMapping("/api/solicitudes-servicio/{ordenId}/pagos")
    public ResponseEntity<ResumenServicioDTO.Cliente> resumenCliente(@PathVariable String ordenId, Authentication auth) {
        return ResponseEntity.ok(resumen.deCliente(auth.getName(), ordenId));
    }

    /** El total de la orden para el SUPERADMIN (lo exige /api/plataforma/** en SecurityConfig). */
    @GetMapping("/api/plataforma/ordenes/{ordenId}/resumen")
    public ResponseEntity<ResumenServicioDTO.Admin> resumenAdmin(@PathVariable String ordenId) {
        return ResponseEntity.ok(resumen.deAdmin(ordenId));
    }
}
