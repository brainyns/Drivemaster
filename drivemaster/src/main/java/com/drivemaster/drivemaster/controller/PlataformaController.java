package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClientesRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.Operacion;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductosRespuesta;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO;
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
import com.drivemaster.drivemaster.service.PlataformaDetalleService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final PlataformaDetalleService detalle;

    public PlataformaController(PlataformaDashboardService dashboardService, PlataformaConsultasService consultas,
                                PlataformaReportesService reportes, PlataformaNegociosService negocios,
                                PlataformaDetalleService detalle) {
        this.dashboardService = dashboardService;
        this.consultas = consultas;
        this.reportes = reportes;
        this.negocios = negocios;
        this.detalle = detalle;
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

    /** SUPERADMIN: detalle de un pedido (productos, partes, línea de tiempo, pago Wompi, reparto y entrega). */
    @GetMapping("/pedidos/{id}")
    public ResponseEntity<DetallePlataformaDTO.Pedido> detallePedido(@PathVariable String id) {
        return ResponseEntity.ok(detalle.pedido(id));
    }

    /** SUPERADMIN: detalle de un cliente (datos, ciudad, vehículos, pedidos y solicitudes de diagnóstico). */
    @GetMapping("/clientes/{id}")
    public ResponseEntity<DetallePlataformaDTO.Cliente> detalleCliente(@PathVariable String id) {
        return ResponseEntity.ok(detalle.cliente(id));
    }

    /** SUPERADMIN: detalle de una tienda o un taller (datos, ubicación, productos o servicios, pedidos y totales). */
    @GetMapping("/negocios/{tipo}/{id}")
    public ResponseEntity<DetallePlataformaDTO.Negocio> detalleNegocio(@PathVariable String tipo, @PathVariable String id) {
        return ResponseEntity.ok(detalle.negocio(tipo, id));
    }

    /** ADMIN y SUPERADMIN: monitor de pedidos y envíos de las tiendas (sin datos del cliente). */
    @GetMapping("/pedidos")
    public ResponseEntity<PedidosRespuesta> pedidos(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) String entrega,
            @RequestParam(defaultValue = "false") boolean atrasados,
            @RequestParam(required = false) String alerta,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            // Alias de antes de page/size: se quitan después de la feria (README, «Alias por quitar»)
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return ResponseEntity.ok(consultas.pedidos(estado, tiendaId, entrega, atrasados ? "ATRASADO" : alerta,
                RangoFechas.de(desde, hasta),
                PeticionTabla.de(page, size, pagina, tamano, sort, PlataformaConsultasService.ORDEN_PEDIDOS)));
    }

    /** SUPERADMIN: tiendas y talleres en una lista, con estado, plan, calificación, productos u órdenes y datos por completar. */
    @GetMapping("/negocios")
    public ResponseEntity<NegociosRespuesta> negocios(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(negocios.negocios(tipo, estado, q, plan, pagina, tamano));
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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            // Alias de antes de page/size: se quitan después de la feria (README, «Alias por quitar»)
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return ResponseEntity.ok(consultas.usuarios(q, rol, estado,
                PeticionTabla.de(page, size, pagina, tamano, sort, PlataformaConsultasService.ORDEN_USUARIOS)));
    }

    /** Pedidos o solicitudes en curso del negocio de un usuario: lo avisa el modal antes de desactivarlo. */
    @GetMapping("/usuarios/{id}/en-curso")
    public ResponseEntity<com.drivemaster.drivemaster.service.PlataformaConsultasService.EnCurso> enCurso(@PathVariable String id) {
        return ResponseEntity.ok(consultas.enCurso(id));
    }

    /** Catálogo global de solo lectura, con filtros por tienda, nuevos, estado y búsqueda. */
    @GetMapping("/productos")
    public ResponseEntity<ProductosRespuesta> productos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) Integer nuevosDias,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            // Alias de antes de page/size: se quitan después de la feria (README, «Alias por quitar»)
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return ResponseEntity.ok(consultas.productos(q, tiendaId, nuevosDias, estado,
                PeticionTabla.de(page, size, pagina, tamano, sort, PlataformaConsultasService.ORDEN_PRODUCTOS)));
    }

    /** Clientes registrados para monitoreo (sin teléfono, dirección ni documento). */
    @GetMapping("/clientes")
    public ResponseEntity<ClientesRespuesta> clientes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            // Alias de antes de page/size: se quitan después de la feria (README, «Alias por quitar»)
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return ResponseEntity.ok(consultas.clientes(q, ciudad, estado,
                PeticionTabla.de(page, size, pagina, tamano, sort, PlataformaConsultasService.ORDEN_CLIENTES)));
    }
}
