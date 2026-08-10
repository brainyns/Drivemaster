package com.drivemaster.drivemaster.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.drivemaster.drivemaster.dto.CompraInmediataRequest;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.VentaService;
import com.drivemaster.drivemaster.util.PdfVentaListBuilder;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final UsuarioRepository usuarioRepository;

    public VentaController(VentaService ventaService, UsuarioRepository usuarioRepository) {
        this.ventaService = ventaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public List<Venta> listar() {
        return ventaService.listarTodas();
    }

    @GetMapping("/mis-ventas")
    public ResponseEntity<List<Venta>> misVentas(Authentication auth) {
        Usuario usuario = usuarioRepository.findByCorreo(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(ventaService.listarPorUsuarioId(usuario.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venta> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(ventaService.obtenerPorId(id));
    }

    @PostMapping("/guardar")
    public ResponseEntity<Venta> guardar(@RequestBody Venta venta) {
        return ResponseEntity.ok(ventaService.registrarVenta(venta));
    }

    @PostMapping("/compra-inmediata")
    public ResponseEntity<Venta> compraInmediata(@RequestBody CompraInmediataRequest request,
                                                  Authentication auth) {
        Venta venta = ventaService.compraInmediata(auth.getName(),
                request.getProductos(), request.getMetodoPago());
        return ResponseEntity.ok(venta);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<Void> anular(@PathVariable String id) {
        ventaService.anularVenta(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Venta> cambiarEstado(@PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ventaService.cambiarEstado(id, body.get("estado")));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String id) {
        try {
            byte[] pdf     = ventaService.generarPdf(id);
            String idCorto = id.substring(Math.max(0, id.length() - 8)).toUpperCase();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Factura-DriveMaster-" + idCorto + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF de factura", e);
        }
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "excel") String formato) {
        List<Venta> lista = ventaService.listarTodas();

        if ("pdf".equalsIgnoreCase(formato)) {
            try {
                byte[] pdf = PdfVentaListBuilder.construir(lista);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Ventas-DriveMaster.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdf);
            } catch (Exception e) {
                throw new RuntimeException("Error al exportar ventas a PDF", e);
            }
        }

        return exportarExcelVentas(lista);
    }

    @GetMapping("/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel() {
        return exportarExcelVentas(ventaService.listarTodas());
    }

    private ResponseEntity<byte[]> exportarExcelVentas(List<Venta> ventas) {
        byte[] excel   = ventaService.exportarExcel();
        String filename = "Ventas-DriveMaster-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
