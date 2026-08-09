package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final SolicitudRepository solicitudRepository;
    private final PagoRepository pagoRepository;
    private final VentaService ventaService;
    private final UsuarioRepository usuarioRepository;
    private final RestTemplate restTemplate;

    @Value("${wompi.public_key}")
    private String publicKey;

    @Value("${wompi.integrity_key}")
    private String integrityKey;

    @Value("${wompi.api_url}")
    private String apiUrl;

    @Value("${wompi.checkout_url}")
    private String checkoutUrl;

    @Value("${wompi.redirect_url}")
    private String redirectUrlDefault;

    public PagoService(SolicitudRepository solicitudRepository,
                       PagoRepository pagoRepository,
                       VentaService ventaService,
                       UsuarioRepository usuarioRepository,
                       RestTemplate restTemplate) {
        this.solicitudRepository = solicitudRepository;
        this.pagoRepository = pagoRepository;
        this.ventaService = ventaService;
        this.usuarioRepository = usuarioRepository;
        this.restTemplate = restTemplate;
    }

    // ─── CREAR LINK DE PAGO (Web Checkout de Wompi) ────────

    public Map<String, Object> crearPago(String solicitudId, String redirectUrl) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        String estado = solicitud.getEstado();
        if (!("PENDIENTE".equals(estado) || "APROBADO".equals(estado))) {
            throw new RuntimeException("La solicitud no está disponible para pago (estado: " + estado + ")");
        }

        String referencia = generarReferencia(solicitudId);
        long montoCentavos = Math.round(solicitud.getTotal() * 100);

        String checkoutUrlFinal = construirUrlCheckout(
                solicitud.getClienteId(), referencia, montoCentavos, redirectUrl);

        PagoEntity pago = new PagoEntity();
        pago.setSolicitudId(solicitudId);
        pago.setReferencia(referencia);
        pago.setMonto(solicitud.getTotal());
        pago.setMetodo("WOMPI");
        pago.setEstado("PENDIENTE");
        pago.setFechaCreacion(LocalDateTime.now());
        pagoRepository.save(pago);

        log.info("Link de pago generado para solicitud {} (ref {})", solicitudId, referencia);

        return Map.of(
                "checkoutUrl", checkoutUrlFinal,
                "referencia", referencia,
                "monto", montoCentavos,
                "firma", generarFirma(referencia, montoCentavos)
        );
    }

    public Map<String, Object> crearPagoVenta(String ventaId, String redirectUrl) {
        Venta venta = ventaService.obtenerPorId(ventaId);

        String estado = venta.getEstado();
        if (!("PENDIENTE".equals(estado) || "PENDIENTE_PAGO".equals(estado) || "APROBADO".equals(estado))) {
            throw new RuntimeException("La venta no está disponible para pago (estado: " + estado + ")");
        }

        String referencia = generarReferencia(ventaId);
        long montoCentavos = Math.round(venta.getTotal() * 100);

        String checkoutUrlFinal = construirUrlCheckout(
                venta.getClienteId(), referencia, montoCentavos, redirectUrl);

        PagoEntity pago = new PagoEntity();
        pago.setVentaId(ventaId);
        pago.setReferencia(referencia);
        pago.setMonto(venta.getTotal());
        pago.setMetodo("WOMPI");
        pago.setEstado("PENDIENTE");
        pago.setFechaCreacion(LocalDateTime.now());
        pagoRepository.save(pago);

        log.info("Link de pago generado para venta {} (ref {})", ventaId, referencia);

        return Map.of(
                "checkoutUrl", checkoutUrlFinal,
                "referencia", referencia,
                "monto", montoCentavos,
                "firma", generarFirma(referencia, montoCentavos)
        );
    }

    private String construirUrlCheckout(String clienteId, String referencia, long montoCentavos, String redirectUrl) {
        String firma = generarFirma(referencia, montoCentavos);

        StringBuilder url = new StringBuilder(checkoutUrl)
                .append("?public-key=").append(enc(publicKey))
                .append("&currency=COP")
                .append("&amount-in-cents=").append(montoCentavos)
                .append("&reference=").append(enc(referencia))
                .append("&signature:integrity=").append(firma);

        String urlRedirect = (redirectUrl != null && !redirectUrl.isBlank())
                ? redirectUrl
                : redirectUrlDefault;
        if (urlRedirect != null && !urlRedirect.isBlank()) {
            url.append("&redirect-url=").append(enc(urlRedirect));
        }

        usuarioRepository.findById(clienteId).ifPresent(u -> {
            if (u.getRegion() == null || u.getRegion().isBlank()) {
                throw new RuntimeException("Complete su región o departamento de envío en sus datos de envío.");
            }
            if (u.getCorreo() != null)          url.append("&customer-data:email=").append(enc(u.getCorreo()));
            if (u.getNombre() != null)          url.append("&customer-data:full-name=").append(enc(u.getNombre()));
            if (u.getTelefono() != null)        url.append("&customer-data:phone-number=").append(enc(u.getTelefono()));
            if (u.getIdentificacion() != null)  url.append("&customer-data:legal-id=").append(enc(u.getIdentificacion()));
            url.append("&customer-data:legal-id-type=CC");
            if (u.getDireccion() != null)       url.append("&shipping-address:address-line-1=").append(enc(u.getDireccion()));
            if (u.getCiudad() != null)          url.append("&shipping-address:city=").append(enc(u.getCiudad()));
            if (u.getRegion() != null)          url.append("&shipping-address:region=").append(enc(u.getRegion()));
            if (u.getTelefono() != null)        url.append("&shipping-address:phone-number=").append(enc(u.getTelefono()));
            url.append("&shipping-address:country=CO");
        });

        return url.toString();
    }

    // ─── VERIFICAR TRANSACCIÓN (cuando el cliente vuelve del checkout) ──

    public Map<String, Object> verificarTransaccion(String transactionId) {
        Map<String, Object> txn = consultarTransaccion(transactionId);
        if (txn == null) {
            return Map.of("estado", "NO_ENCONTRADO", "solicitudId", "");
        }
        String status = str(txn.get("status"));
        String solicitudId = "";
        if ("APPROVED".equals(status)) {
            solicitudId = confirmarPagoPorReferencia(str(txn.get("reference")), transactionId);
        }
        return Map.of("estado", status, "solicitudId", solicitudId != null ? solicitudId : "");
    }

    // ─── WEBHOOK (Wompi notifica el evento) ─────────────────

    public void procesarWebhook(Map<String, Object> body) {
        log.info("Webhook Wompi recibido: {}", body != null ? body.get("event") : "null");
        try {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return;
            Map<String, Object> transaction = (Map<String, Object>) data.get("transaction");
            if (transaction == null) return;

            String transactionId = str(transaction.get("id"));
            String status = str(transaction.get("status"));
            String reference = str(transaction.get("reference"));

            log.info("Evento {} -> txn {} status {}", body.get("event"), transactionId, status);

            if ("APPROVED".equals(status) || "APPROVED".equals(consultarEstadoTransaccion(transactionId))) {
                confirmarPagoPorReferencia(reference, transactionId);
            }
        } catch (Exception e) {
            log.error("Error procesando webhook de Wompi", e);
        }
    }

    // ─── CONFIRMAR PAGO (idempotente) ───────────────────────

    private String confirmarPagoPorReferencia(String referencia, String transactionId) {
        PagoEntity pagoEntity = referencia != null
                ? pagoRepository.findByReferencia(referencia).orElse(null)
                : null;
        if (pagoEntity == null) {
            log.warn("No se encontró pago con referencia {}", referencia);
            return null;
        }
        if ("PAGADO".equals(pagoEntity.getEstado())) {
            return pagoEntity.getVentaId() != null ? pagoEntity.getVentaId() : pagoEntity.getSolicitudId();
        }

        String ventaId = pagoEntity.getVentaId();
        if (ventaId != null) {
            Venta venta = ventaService.confirmarPagoVenta(ventaId, referencia);
            pagoEntity.setEstado("PAGADO");
            pagoEntity.setTransactionId(transactionId);
            pagoEntity.setFechaPago(LocalDateTime.now());
            pagoRepository.save(pagoEntity);
            log.info("Venta {} marcada PAGADA (txn {})", ventaId, transactionId);
            return ventaId;
        }

        String solicitudId = pagoEntity.getSolicitudId();
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if ("PAGADO".equals(solicitud.getEstado())) {
            return solicitudId;
        }

        Venta venta = new Venta();
        venta.setClienteId(solicitud.getClienteId());
        venta.setUsuarioId(solicitud.getUsuarioId());
        venta.setFecha(LocalDateTime.now());
        venta.setEstado("PAGADA");
        venta.setTipoVenta("WEB");
        venta.setProductos(solicitud.getProductos());
        venta.setTotal(solicitud.getTotal());
        venta.setSolicitudId(solicitud.getId());

        Pago pagoObj = new Pago("WOMPI", solicitud.getTotal(), LocalDateTime.now(), referencia);
        venta.setPagos(List.of(pagoObj));

        Venta ventaGuardada = ventaService.registrarVenta(venta);

        solicitud.setEstado("PAGADO");
        solicitud.setVentaId(ventaGuardada.getId());
        solicitud.setFechaActualizacion(LocalDateTime.now());
        solicitudRepository.save(solicitud);

        pagoEntity.setEstado("PAGADO");
        pagoEntity.setTransactionId(transactionId);
        pagoEntity.setFechaPago(LocalDateTime.now());
        pagoRepository.save(pagoEntity);

        log.info("Solicitud {} marcada PAGADO (venta {})", solicitudId, ventaGuardada.getId());
        return solicitudId;
    }

    // ─── Wompi API ──────────────────────────────────────────

    private Map<String, Object> consultarTransaccion(String transactionId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(publicKey);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    apiUrl + "/transactions/" + transactionId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("Wompi API respondió {} al consultar {}", resp.getStatusCode(), transactionId);
                return null;
            }
            return (Map<String, Object>) resp.getBody().get("data");
        } catch (Exception e) {
            log.error("Error consultando transacción {} en Wompi", transactionId, e);
            return null;
        }
    }

    private String consultarEstadoTransaccion(String transactionId) {
        Map<String, Object> txn = consultarTransaccion(transactionId);
        return txn != null ? str(txn.get("status")) : null;
    }

    // ─── FIRMA / REFERENCIA ─────────────────────────────────

    private String generarReferencia(String solicitudId) {
        String timePart = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        return "DRV-" + solicitudId.toUpperCase().substring(0, Math.min(6, solicitudId.length())) + timePart;
    }

    private String generarFirma(String referencia, long montoCentavos) {
        try {
            String data = referencia + montoCentavos + "COP" + integrityKey;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar firma", e);
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
