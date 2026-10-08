package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClientesRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.Operacion;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.service.PlataformaConsultasService;
import com.drivemaster.drivemaster.service.PlataformaDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de la plataforma. El acceso por rol lo define SecurityConfig: /api/plataforma/** es solo SUPERADMIN,
 * salvo /operacion (panel mínimo del ADMIN).
 */
@RestController
@RequestMapping("/api/plataforma")
public class PlataformaController {

    private final PlataformaDashboardService dashboardService;
    private final PlataformaConsultasService consultas;

    public PlataformaController(PlataformaDashboardService dashboardService, PlataformaConsultasService consultas) {
        this.dashboardService = dashboardService;
        this.consultas = consultas;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardPlataformaDTO> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }

    /** ADMIN y SUPERADMIN: lo que hay que atender hoy. */
    @GetMapping("/operacion")
    public ResponseEntity<Operacion> operacion() {
        return ResponseEntity.ok(dashboardService.operacion());
    }

    /** Catálogo global de solo lectura, con filtros por tienda, nuevos, estado y búsqueda. */
    @GetMapping("/productos")
    public ResponseEntity<ProductosRespuesta> productos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) Integer nuevosDias,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(consultas.productos(q, tiendaId, nuevosDias, estado, pagina, tamano));
    }

    /** Clientes registrados para monitoreo (sin teléfono, dirección ni documento). */
    @GetMapping("/clientes")
    public ResponseEntity<ClientesRespuesta> clientes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(consultas.clientes(q, ciudad, estado, pagina, tamano));
    }
}
