package com.drivemaster.drivemaster.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.dto.PerfilDTO;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.VentaRepository;

@RestController
@RequestMapping("/api/perfil")
@CrossOrigin(origins = "http://localhost:5173")
public class PerfilController {

    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final SolicitudRepository solicitudRepository;
    private final ProductoRepository productoRepository;

    public PerfilController(UsuarioRepository usuarioRepository,
            VentaRepository ventaRepository,
            SolicitudRepository solicitudRepository,
            ProductoRepository productoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.ventaRepository = ventaRepository;
        this.solicitudRepository = solicitudRepository;
        this.productoRepository = productoRepository;
    }

    @GetMapping
    public ResponseEntity<PerfilDTO> getPerfil(Authentication auth) {
        Usuario usuario = usuarioRepository.findByCorreo(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<String, Object> stats = buildStats(usuario);

        PerfilDTO dto = PerfilDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .ultimoLogin(usuario.getUltimoLogin())
                .fechaCreacion(usuario.getFechaCreacion())
                .stats(stats)
                .build();

        return ResponseEntity.ok(dto);
    }

    private Map<String, Object> buildStats(Usuario usuario) {
        Map<String, Object> stats = new HashMap<>();
        String rol = usuario.getRol();

        List<Venta> todasLasVentas = ventaRepository.findAll();
        List<Venta> ventasDelUsuario = ventaRepository.findByUsuarioId(usuario.getId());

        long totalVentas = todasLasVentas.size();
        double totalIngresos = todasLasVentas.stream()
                .filter(v -> "PAGADA".equals(v.getEstado()))
                .mapToDouble(Venta::getTotal)
                .sum();

        long solicitudesAprobadas = solicitudRepository.findByEstado("APROBADO").size();
        long totalProductos = productoRepository.findAll().size();
        long totalClientes = usuarioRepository.findByRol("CLIENTE").size();

        LocalDateTime hoyInicio = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime hoyFin = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        List<Venta> ventasHoy = ventaRepository.findByFechaBetween(hoyInicio, hoyFin);
        long ventasDelDia = ventasHoy.size();

        List<Producto> stockBajo = productoRepository.findProductosConStockBajo();
        List<Solicitud> solicitudesPendientes = solicitudRepository.findByEstado("PENDIENTE_PAGO");
        solicitudesPendientes.addAll(solicitudRepository.findByEstado("PAGO_VERIFICADO"));

        stats.put("totalVentas", totalVentas);
        stats.put("totalIngresos", totalIngresos);
        stats.put("totalProductos", totalProductos);
        stats.put("totalClientes", totalClientes);
        stats.put("ventasDelDia", ventasDelDia);
        stats.put("solicitudesAprobadas", solicitudesAprobadas);
        stats.put("stockBajo", stockBajo.size());
        stats.put("solicitudesPendientes", solicitudesPendientes.size());

        if ("ADMIN".equals(rol) || "SUPERADMIN".equals(rol)) {
            stats.put("productosAgregados", totalProductos);
        }

        if ("VENDEDOR".equals(rol)) {
            long clientesAtendidos = ventasDelUsuario.stream()
                    .map(Venta::getClienteId)
                    .distinct()
                    .count();
            stats.put("clientesAtendidos", clientesAtendidos);
            stats.put("ventasRealizadas", (long) ventasDelUsuario.size());
        }

        if ("SUPERADMIN".equals(rol)) {
            long totalUsuarios = usuarioRepository.findAll().size();
            stats.put("totalUsuarios", totalUsuarios);
            stats.put("totalUsuariosStaff", totalUsuarios - totalClientes);
        }

        return stats;
    }
}
