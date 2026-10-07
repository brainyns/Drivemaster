package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.util.EmailSolicitudBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private static final Set<String> ROLES_INTERNOS = Set.of("SUPERADMIN", "ADMIN", "VENDEDOR");

    private final SolicitudRepository solicitudRepository;
    private final PagoRepository pagoRepository;
    private final VentaService ventaService;
    private final UsuarioRepository usuarioRepository;
    private final EmailsService emailsService;
    private final RestTemplate restTemplate;
    // @Lazy: PagoMarketplaceService también usa este servicio para crear el link
    private final PagoMarketplaceService pagoMarketplace;

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

    // Solo desarrollo (PAGO_SIMULADO_ENABLED); además exige llaves pub_test_
    @Value("${drivemaster.pago-simulado.enabled:false}")
    private boolean pagoSimuladoHabilitado;

    // Solo desarrollo (PAGO_VERIFICACION_MANUAL_ENABLED): ofrece "Ya pagué, verificar mi pago"; exige pub_test_
    @Value("${drivemaster.pago-verificacion-manual.enabled:false}")
    private boolean verificacionManualHabilitada;

    public PagoService(SolicitudRepository solicitudRepository,
                       PagoRepository pagoRepository,
                       VentaService ventaService,
                       UsuarioRepository usuarioRepository,
                       EmailsService emailsService,
                       RestTemplate restTemplate,
                       @Lazy PagoMarketplaceService pagoMarketplace) {
        this.pagoMarketplace = pagoMarketplace;
        this.solicitudRepository = solicitudRepository;
        this.pagoRepository = pagoRepository;
        this.ventaService = ventaService;
        this.usuarioRepository = usuarioRepository;
        this.emailsService = emailsService;
        this.restTemplate = restTemplate;
    }

    // ─── CREAR LINK DE PAGO (Web Checkout de Wompi) ────────

    /** Link de pago pedido por un usuario: valida que la solicitud sea suya. */
    public Map<String, Object> crearPago(String solicitudId, String redirectUrl, String correoUsuario) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        validarPropietario(solicitud.getClienteId(), correoUsuario);
        return crearPago(solicitudId, redirectUrl);
    }

    /** Link de pago generado por el sistema (al aprobar un encargo), sin usuario en sesión. */
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
                solicitud.getClienteId(), referencia, montoCentavos, redirectUrl, true);

        PagoEntity pago = new PagoEntity();
        pago.setSolicitudId(solicitudId);
        pago.setConcepto("SOLICITUD");
        pago.setUsuarioId(solicitud.getClienteId());
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

    public Map<String, Object> crearPagoVenta(String ventaId, String redirectUrl, String correoUsuario) {
        Venta venta = ventaService.obtenerPorId(ventaId);
        validarPropietario(venta.getClienteId(), correoUsuario);

        String estado = venta.getEstado();
        if (!("PENDIENTE".equals(estado) || "PENDIENTE_PAGO".equals(estado) || "APROBADO".equals(estado))) {
            throw new RuntimeException("La venta no está disponible para pago (estado: " + estado + ")");
        }

        String referencia = generarReferencia(ventaId);
        long montoCentavos = Math.round(venta.getTotal() * 100);

        String checkoutUrlFinal = construirUrlCheckout(
                venta.getClienteId(), referencia, montoCentavos, redirectUrl, true);

        PagoEntity pago = new PagoEntity();
        pago.setVentaId(ventaId);
        pago.setConcepto("VENTA");
        pago.setUsuarioId(venta.getClienteId());
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

    /**
     * Link de pago del marketplace (DIAGNOSTICO o COTIZACION). Quien llama ya validó
     * que el cliente es el dueño y que la orden espera ese pago. Sin dirección de
     * envío: el servicio se presta en el taller.
     */
    public Map<String, Object> crearPagoMarketplace(String concepto, String usuarioId, double monto,
                                                    String ordenId, String cotizacionId) {
        return crearPagoMarketplace(concepto, usuarioId, monto, ordenId, cotizacionId, null);
    }

    /** Pago de una compra directa del catálogo (concepto CATALOGO): un solo pago de Wompi para varias tiendas. */
    public Map<String, Object> crearPagoCatalogo(String usuarioId, double monto, String compraId) {
        return crearPagoMarketplace("CATALOGO", usuarioId, monto, null, null, compraId);
    }

    private Map<String, Object> crearPagoMarketplace(String concepto, String usuarioId, double monto,
                                                     String ordenId, String cotizacionId, String compraId) {
        String referencia = generarReferencia(compraId != null ? compraId : ordenId);
        long montoCentavos = Math.round(monto * 100);
        String checkoutUrlFinal = construirUrlCheckout(usuarioId, referencia, montoCentavos, null, false);

        PagoEntity pago = new PagoEntity();
        pago.setConcepto(concepto);
        pago.setOrdenServicioId(ordenId);
        pago.setCotizacionId(cotizacionId);
        pago.setCompraCatalogoId(compraId);
        pago.setUsuarioId(usuarioId);
        pago.setReferencia(referencia);
        pago.setMonto(montoCentavos / 100.0);
        pago.setMetodo("WOMPI");
        pago.setEstado("PENDIENTE");
        pago.setFechaCreacion(LocalDateTime.now());
        pagoRepository.save(pago);

        log.info("Link de pago {} generado para {} (ref {})", concepto, compraId != null ? "la compra " + compraId : "la orden " + ordenId, referencia);
        return Map.of(
                "checkoutUrl", checkoutUrlFinal,
                "referencia", referencia,
                "monto", montoCentavos,
                // Sin redirect-url (desarrollo local): el cliente verifica el pago a mano
                "verificacionManual", verificacionManualDisponible(),
                "pagoSimulado", pagoSimuladoDisponible());
    }

    // ─── Redirección y pago simulado (desarrollo) ─────────

    /**
     * URL a la que Wompi devuelve al cliente, o null si no se debe enviar. El
     * checkout de Wompi responde 403 cuando redirect-url apunta a localhost, así que
     * en desarrollo local se omite y el cliente verifica su pago desde la app.
     */
    private String redirectEfectivo(String redirectUrl) {
        String url = (redirectUrl != null && !redirectUrl.isBlank()) ? redirectUrl : redirectUrlDefault;
        if (url == null || url.isBlank() || esUrlLocal(url)) return null;
        return url;
    }

    static boolean esUrlLocal(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * "Ya pagué, verificar mi pago": solo con PAGO_VERIFICACION_MANUAL_ENABLED=true, llaves
     * de pruebas de Wompi (pub_test_) y sin redirect-url (localhost). En producción nunca
     * es true: el flag va apagado por defecto y las llaves y la URL son reales.
     */
    public boolean verificacionManualDisponible() {
        return verificacionManualHabilitada && publicKey != null && publicKey.startsWith("pub_test_")
                && redirectEfectivo(null) == null;
    }

    /** Solo con PAGO_SIMULADO_ENABLED=true y llaves de pruebas de Wompi (pub_test_…). */
    public boolean pagoSimuladoDisponible() {
        return pagoSimuladoHabilitado && publicKey != null && publicKey.startsWith("pub_test_");
    }

    @PostConstruct
    void avisarPagoSimulado() {
        if (verificacionManualHabilitada && !verificacionManualDisponible()) {
            log.warn("PAGO_VERIFICACION_MANUAL_ENABLED se ignora: requiere llaves de pruebas de Wompi (pub_test_) y redirect-url local");
        }
        if (!pagoSimuladoHabilitado) return;
        if (pagoSimuladoDisponible()) {
            log.warn("PAGO SIMULADO ACTIVO: solo para desarrollo. Nunca activar PAGO_SIMULADO_ENABLED en producción.");
        } else {
            log.warn("PAGO_SIMULADO_ENABLED se ignora: requiere llaves de pruebas de Wompi (pub_test_)");
        }
    }

    /**
     * Desarrollo: da por aprobado un pago del marketplace sin pasar por Wompi. Usa
     * el mismo código de confirmación (monto, idempotencia, efectos) que un pago
     * real. Solo el cliente dueño del pago.
     */
    public Map<String, Object> simularPagoAprobado(String referencia, String correoUsuario) {
        if (!pagoSimuladoDisponible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No disponible");
        }
        PagoEntity pago = pagoRepository.findByReferencia(referencia)
                .filter(p -> PagoMarketplaceService.esMarketplace(p.getConcepto()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (!usuario.getId().equals(pago.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este pago no le pertenece");
        }
        Map<String, Object> txn = new HashMap<>();
        txn.put("id", "SIMULADO-" + referencia);
        txn.put("reference", referencia);
        txn.put("amount_in_cents", Math.round(pago.getMonto() * 100));
        txn.put("currency", "COP");
        txn.put("status", "APPROVED");
        log.warn("Pago simulado (desarrollo) para la referencia {}", referencia);
        confirmarPagoAprobado(txn);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("estado", "APPROVED");
        resultado.put("concepto", pago.getConcepto());
        resultado.put("ordenId", pago.getOrdenServicioId());
        pagoRepository.findByReferencia(referencia)
                .ifPresent(p -> resultado.putAll(pagoMarketplace.resumenAplicacion(p)));
        return resultado;
    }

    /**
     * Solo el cliente dueño puede generar el link de pago. El personal interno
     * también puede (desde Solicitudes se genera el link para el cliente).
     */
    private void validarPropietario(String clienteId, String correoUsuario) {
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (ROLES_INTERNOS.contains(usuario.getRol())) return;
        if (clienteId == null || !clienteId.equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este pago no le pertenece");
        }
    }

    private String construirUrlCheckout(String clienteId, String referencia, long montoCentavos, String redirectUrl,
                                        boolean conEnvio) {
        String firma = generarFirma(referencia, montoCentavos);

        StringBuilder url = new StringBuilder(checkoutUrl)
                .append("?public-key=").append(enc(publicKey))
                .append("&currency=COP")
                .append("&amount-in-cents=").append(montoCentavos)
                .append("&reference=").append(enc(referencia))
                .append("&signature:integrity=").append(firma);

        String urlRedirect = redirectEfectivo(redirectUrl);
        if (urlRedirect != null) {
            url.append("&redirect-url=").append(enc(urlRedirect));
        }

        usuarioRepository.findById(clienteId).ifPresent(u -> {
            if (!conEnvio) {
                if (u.getCorreo() != null) url.append("&customer-data:email=").append(enc(u.getCorreo()));
                if (u.getNombre() != null) url.append("&customer-data:full-name=").append(enc(u.getNombre()));
                return;
            }
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
            solicitudId = confirmarPagoAprobado(txn);
        } else {
            marcarSiRechazado(txn);
        }
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("estado", status);
        resultado.put("solicitudId", solicitudId != null ? solicitudId : "");
        // Pagos del marketplace: la pantalla de resultado lleva a la solicitud
        String referencia = str(txn.get("reference"));
        if (referencia != null) {
            pagoRepository.findByReferencia(referencia)
                    .filter(p -> PagoMarketplaceService.esMarketplace(p.getConcepto()))
                    .ifPresent(p -> {
                        resultado.put("concepto", p.getConcepto());
                        resultado.put("ordenId", p.getOrdenServicioId());
                        // "estado" es lo que dice Wompi; esto dice si DriveMaster lo aplicó
                        resultado.putAll(pagoMarketplace.resumenAplicacion(p));
                    });
        }
        return resultado;
    }

    /** Pago declinado o anulado según la API de Wompi: el pago pendiente queda RECHAZADO. */
    private void marcarSiRechazado(Map<String, Object> txn) {
        String status = str(txn.get("status"));
        String referencia = str(txn.get("reference"));
        if (referencia == null || !Set.of("DECLINED", "ERROR", "VOIDED").contains(status)) return;
        pagoRepository.findByReferencia(referencia)
                .ifPresent(p -> pagoRepository.marcarRechazado(p.getId(), str(txn.get("id"))));
    }

    // ─── WEBHOOK (Wompi notifica el evento) ─────────────────

    /**
     * El body del webhook solo se usa para saber QUÉ transacción revisar. El estado,
     * la referencia y el monto se toman siempre de la API de Wompi: un webhook
     * falsificado con status APPROVED ya no puede marcar un pago como pagado.
     */
    public void procesarWebhook(Map<String, Object> body) {
        log.info("Webhook Wompi recibido: {}", body != null ? body.get("event") : "null");
        try {
            if (body == null || !(body.get("data") instanceof Map<?, ?> data)) return;
            if (!(data.get("transaction") instanceof Map<?, ?> transaction)) return;

            String transactionId = str(transaction.get("id"));
            if (transactionId == null || transactionId.isBlank()) return;

            Map<String, Object> txn = consultarTransaccion(transactionId);
            if (txn == null) {
                log.warn("Webhook: no se pudo consultar la transacción {} en Wompi", transactionId);
                return;
            }

            String status = str(txn.get("status"));
            log.info("Evento {} -> txn {} status {} (verificado con la API)", body.get("event"), transactionId, status);

            if ("APPROVED".equals(status)) {
                confirmarPagoAprobado(txn);
            } else {
                marcarSiRechazado(txn);
            }
        } catch (Exception e) {
            log.error("Error procesando webhook de Wompi", e);
        }
    }

    // ─── CONFIRMAR PAGO (idempotente) ───────────────────────

    /**
     * Confirma un pago a partir de la transacción consultada en la API de Wompi.
     * Antes de dar el pago por bueno verifica que monto y moneda coincidan con lo
     * que se cobró al generar el link.
     */
    private String confirmarPagoAprobado(Map<String, Object> txn) {
        String referencia = str(txn.get("reference"));
        String transactionId = str(txn.get("id"));

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

        long esperadoCentavos = Math.round(pagoEntity.getMonto() * 100);
        Long recibidoCentavos = txn.get("amount_in_cents") instanceof Number n ? n.longValue() : null;
        String moneda = str(txn.get("currency"));
        if (recibidoCentavos == null || recibidoCentavos != esperadoCentavos || !"COP".equals(moneda)) {
            log.error("Pago {} rechazado: Wompi reporta {} {} y se esperaban {} COP (txn {})",
                    referencia, recibidoCentavos, moneda, esperadoCentavos, transactionId);
            pagoEntity.setEstado("MONTO_INVALIDO");
            pagoEntity.setTransactionId(transactionId);
            pagoRepository.save(pagoEntity);
            return null;
        }

        // Marketplace: toma el pago de forma atómica y aplica sus efectos (idempotente)
        if (PagoMarketplaceService.esMarketplace(pagoEntity.getConcepto())) {
            pagoMarketplace.confirmar(pagoEntity, transactionId);
            return pagoEntity.getOrdenServicioId();
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

        usuarioRepository.findById(solicitud.getClienteId()).ifPresent(usuario ->
                emailsService.enviarEmail(usuario.getCorreo(),
                        "Hemos recibido tu pago en DriveMaster",
                        EmailSolicitudBuilder.pagoRecibido(usuario, solicitud.getTotal())));

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
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    apiUrl + "/transactions/" + transactionId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("Wompi API respondió {} al consultar {}", resp.getStatusCode(), transactionId);
                return null;
            }
            return toCadenaObjetoMap(resp.getBody().get("data"));
        } catch (Exception e) {
            log.error("Error consultando transacción {} en Wompi", transactionId, e);
            return null;
        }
    }

    /** Convierte un objeto deserializado (Map anidado) a un Map<String, Object> sin casts unchecked. */
    private static Map<String, Object> toCadenaObjetoMap(Object valor) {
        if (!(valor instanceof Map<?, ?> mapa)) return null;
        Map<String, Object> resultado = new HashMap<>();
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            if (entrada.getKey() instanceof String clave) {
                resultado.put(clave, entrada.getValue());
            }
        }
        return resultado;
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
