package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClientesRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.Operacion;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ClientesConMasCompras;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ComisionNeta;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ProductosMasVendidos;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ServiciosPorTaller;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.UsuariosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.VentasPorTienda;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegocioPendiente;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegociosRespuesta;
import com.drivemaster.drivemaster.service.PlataformaConsultasService;
import com.drivemaster.drivemaster.service.PlataformaNegociosService;
import com.drivemaster.drivemaster.service.PlataformaReportesService;
import com.drivemaster.drivemaster.service.PlataformaDashboardService;
import java.util.List;
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
    private final PlataformaReportesService reportes;
    private final PlataformaNegociosService negocios;

    public PlataformaController(PlataformaDashboardService dashboardService, PlataformaConsultasService consultas,
                                PlataformaReportesService reportes, PlataformaNegociosService negocios) {
        this.dashboardService = dashboardService;
        this.consultas = consultas;
        this.reportes = reportes;
        this.negocios = negocios;
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

    /** ADMIN y SUPERADMIN: monitor de pedidos y envíos de las tiendas (sin datos del cliente). */
    @GetMapping("/pedidos")
    public ResponseEntity<PedidosRespuesta> pedidos(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) String entrega,
            @RequestParam(defaultValue = "false") boolean atrasados,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(consultas.pedidos(estado, tiendaId, entrega, atrasados, pagina, tamano));
    }

    /** SUPERADMIN: tiendas y talleres en una lista, con estado, plan, calificación, productos u órdenes y datos por completar. */
    @GetMapping("/negocios")
    public ResponseEntity<NegociosRespuesta> negocios(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(negocios.negocios(tipo, estado, q, pagina, tamano));
    }

    /** ADMIN y SUPERADMIN: negocios por revisar, con lo mínimo para aprobarlos o rechazarlos. */
    @GetMapping("/negocios/pendientes")
    public ResponseEntity<List<NegocioPendiente>> pendientes() {
        return ResponseEntity.ok(negocios.pendientes());
    }

    // ─── Reportes financieros (solo SUPERADMIN) ──────────────────────────────

    @GetMapping("/reportes/ventas")
    public ResponseEntity<VentasPorTienda> ventasPorTienda(@RequestParam(defaultValue = "12") int meses) {
        return ResponseEntity.ok(reportes.ventasPorTienda(meses));
    }

    @GetMapping("/reportes/productos")
    public ResponseEntity<ProductosMasVendidos> productosMasVendidos(@RequestParam(defaultValue = "12") int meses,
                                                                     @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok(reportes.productosMasVendidos(meses, limite));
    }

    @GetMapping("/reportes/clientes")
    public ResponseEntity<ClientesConMasCompras> clientesConMasCompras(@RequestParam(defaultValue = "12") int meses,
                                                                       @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok(reportes.clientesConMasCompras(meses, limite));
    }

    @GetMapping("/reportes/servicios")
    public ResponseEntity<ServiciosPorTaller> serviciosPorTaller() {
        return ResponseEntity.ok(reportes.serviciosPorTaller());
    }

    @GetMapping("/reportes/comision")
    public ResponseEntity<ComisionNeta> comisionNeta(@RequestParam(defaultValue = "12") int meses) {
        return ResponseEntity.ok(reportes.comisionNeta(meses));
    }

    /** Usuarios del sistema con filtros por rol y estado, búsqueda y correo enmascarado. */
    @GetMapping("/usuarios")
    public ResponseEntity<UsuariosRespuesta> usuarios(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(consultas.usuarios(q, rol, estado, pagina, tamano));
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
