package com.drivemaster.drivemaster.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.dto.CartItemRequest;
import com.drivemaster.drivemaster.dto.SolicitudDTO;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;

@Service
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final VentaService ventaService;
    private final EmailsService emailsService;
    private final ParametroRepository parametroRepo;

    public SolicitudService(SolicitudRepository solicitudRepository,
                            UsuarioRepository usuarioRepository,
                            ProductoRepository productoRepository,
                            VentaService ventaService,
                            EmailsService emailsService,
                            ParametroRepository parametroRepo) {
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.ventaService = ventaService;
        this.emailsService = emailsService;
        this.parametroRepo = parametroRepo;
    }

    private Usuario resolverUsuario(String email) {
        return usuarioRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + email));
    }

    private double resolverIva() {
        return parametroRepo.findByClave("IVA")
                .map(p -> Double.parseDouble(p.getValor()) / 100.0)
                .orElse(0.16);
    }

    // ─── CREAR ──────────────────────────────────────────────

    public SolicitudDTO crearSolicitud(String email, List<CartItemRequest> productos, String metodoPago) {
        Usuario usuario = resolverUsuario(email);

        if (usuario.getTelefono() == null || usuario.getDireccion() == null || usuario.getCiudad() == null) {
            throw new RuntimeException("Complete sus datos de envío primero.");
        }

        if (productos == null || productos.isEmpty()) {
            throw new RuntimeException("La solicitud debe contener al menos un producto.");
        }

        List<DetalleVenta> detalles = productos.stream().map(req -> {
            Producto prod = productoRepository.findById(req.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + req.getProductoId()));

            String tipo = prod.getTipo() != null ? prod.getTipo() : "STOCK";

            if ("STOCK".equals(tipo) && req.getCantidad() > prod.getStockActual()) {
                throw new StockInsuficienteException(prod.getNombre(), prod.getStockActual());
            }

            DetalleVenta d = new DetalleVenta();
            d.setProductoId(prod.getId());
            d.setNombre(prod.getNombre());
            d.setImagenUrl(prod.getImagenUrl());
            d.setPrecioUnitario(prod.getPrecioVenta());
            d.setCantidad(req.getCantidad());
            d.setSubtotal(prod.getPrecioVenta() * req.getCantidad());
            d.setTipo(tipo);
            d.setStockActual(prod.getStockActual());
            return d;
        }).collect(Collectors.toList());

        double subtotal = detalles.stream().mapToDouble(DetalleVenta::getSubtotal).sum();
        double iva = subtotal * resolverIva();
        double total = subtotal + iva;

        Solicitud solicitud = new Solicitud();
        solicitud.setClienteId(usuario.getId());
        solicitud.setUsuarioId(usuario.getId());
        solicitud.setProductos(detalles);
        solicitud.setSubtotal(subtotal);
        solicitud.setIva(iva);
        solicitud.setTotal(total);
        solicitud.setMetodoPago(metodoPago);
        solicitud.setEstado("PENDIENTE");
        solicitud.setFechaCreacion(LocalDateTime.now());
        solicitud.setFechaActualizacion(LocalDateTime.now());

        return toDTO(solicitudRepository.save(solicitud));
    }

    // ─── LISTAR ──────────────────────────────────────────────

    public List<SolicitudDTO> listarTodas() {
        return solicitudRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SolicitudDTO> listarPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return solicitudRepository.findByUsuarioId(usuario.getId()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SolicitudDTO obtenerPorId(String id) {
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        return toDTO(solicitud);
    }

    // ─── ADMIN: APROBAR / RECHAZAR ──────────────────────────

    public SolicitudDTO aprobar(String id) {
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        solicitud.setEstado("APROBADO");
        solicitud.setFechaActualizacion(LocalDateTime.now());

        Venta venta = new Venta();
        venta.setClienteId(solicitud.getClienteId());
        venta.setUsuarioId(solicitud.getUsuarioId());
        venta.setFecha(LocalDateTime.now());
        venta.setEstado("APROBADO");
        venta.setTipoVenta("WEB");
        venta.setProductos(solicitud.getProductos());
        venta.setTotal(solicitud.getTotal());
        venta.setSolicitudId(solicitud.getId());

        Pago pago = new Pago(solicitud.getMetodoPago(), solicitud.getTotal(),
                LocalDateTime.now(), "SOL_" + solicitud.getId());
        venta.setPagos(List.of(pago));

        Venta ventaGuardada = ventaService.registrarVenta(venta);

        solicitud.setVentaId(ventaGuardada.getId());
        return toDTO(solicitudRepository.save(solicitud));
    }

    public SolicitudDTO rechazar(String id, String motivo) {
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        solicitud.setEstado("RECHAZADO");
        solicitud.setObservaciones(motivo);
        solicitud.setFechaActualizacion(LocalDateTime.now());
        SolicitudDTO dto = toDTO(solicitudRepository.save(solicitud));

        usuarioRepository.findById(solicitud.getClienteId()).ifPresent(usuario -> {
            String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                    + "body{font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0}"
                    + ".container{max-width:600px;margin:20px auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,.1)}"
                    + ".header{background:#e05a2b;padding:24px;text-align:center}"
                    + ".header h1{color:#fff;margin:0;font-size:22px;letter-spacing:2px;text-transform:uppercase}"
                    + ".body{padding:32px 24px;color:#333}"
                    + ".body h2{color:#e05a2b;font-size:18px;margin:0 0 16px}"
                    + ".body p{font-size:14px;line-height:1.6;color:#555;margin:0 0 12px}"
                    + ".motivo{background:#fff5f5;border:1px solid #ffcaca;border-radius:6px;padding:16px;margin:16px 0;font-size:14px;color:#c00}"
                    + ".footer{background:#fafafa;padding:20px 24px;text-align:center;border-top:1px solid #eee}"
                    + ".footer p{font-size:12px;color:#999;margin:0}"
                    + ".footer .brand{color:#e05a2b;font-weight:700;letter-spacing:1px}"
                    + "</style></head><body>"
                    + "<div class='container'>"
                    + "<div class='header'><h1>DriveMaster</h1></div>"
                    + "<div class='body'>"
                    + "<h2>Actualización de tu pedido</h2>"
                    + "<p>Hola <strong>" + usuario.getNombre() + "</strong>,</p>"
                    + "<p>Lamentablemente tu pedido no pudo ser procesado correctamente por el siguiente motivo:</p>"
                    + "<div class='motivo'>" + motivo + "</div>"
                    + "<p>Si tienes dudas, contáctanos por WhatsApp al 573015335263.</p>"
                    + "</div>"
                    + "<div class='footer'>"
                    + "<p class='brand'>DriveMaster — Automotive Engine</p>"
                    + "<p>© 2025 DriveMaster. Todos los derechos reservados.</p>"
                    + "</div></div></body></html>";
            emailsService.enviarEmail(usuario.getCorreo(),
                    "Actualización de tu pedido en DriveMaster", html);
        });

        return dto;
    }

    // ─── DTO ─────────────────────────────────────────────────

    private SolicitudDTO toDTO(Solicitud s) {
        SolicitudDTO dto = new SolicitudDTO();
        dto.setId(s.getId());
        dto.setClienteId(s.getClienteId());
        dto.setProductos(s.getProductos());
        dto.setSubtotal(s.getSubtotal());
        dto.setIva(s.getIva());
        dto.setTotal(s.getTotal());
        dto.setMetodoPago(s.getMetodoPago());
        dto.setObservaciones(s.getObservaciones());
        dto.setEstado(s.getEstado());
        dto.setFechaCreacion(s.getFechaCreacion());
        dto.setFechaActualizacion(s.getFechaActualizacion());
        dto.setVentaId(s.getVentaId());

        usuarioRepository.findById(s.getClienteId()).ifPresent(usuario -> {
            dto.setClienteNombre(usuario.getNombre());
            dto.setClienteCorreo(usuario.getCorreo());
            dto.setClienteTelefono(usuario.getTelefono());
            dto.setClienteCiudad(usuario.getCiudad());
            dto.setClienteIdentificacion(usuario.getIdentificacion());
        });

        return dto;
    }
}
