package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.CuentaBancaria;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.util.Dinero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de dispersión automática de liquidaciones a través de Wompi Payouts (Pagos a Terceros).
 * Transfiere a cuentas bancarias registradas de talleres y tiendas.
 * Si no hay API key configurada o se usan llaves de prueba, opera en modo sandbox/simulado con
 * validación estricta de cuentas bancarias.
 */
public class WompiPayoutService implements DispersionService {

    private static final Logger log = LoggerFactory.getLogger(WompiPayoutService.class);
    public static final String MODO = "WOMPI";

    private final TallerRepository tallerRepository;
    private final TiendaRepository tiendaRepository;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String userPrincipalId;
    private final String apiUrl;
    private final String publicKey;

    public WompiPayoutService(TallerRepository tallerRepository,
                              TiendaRepository tiendaRepository,
                              RestTemplate restTemplate,
                              String apiKey,
                              String userPrincipalId,
                              String apiUrl,
                              String publicKey) {
        this.tallerRepository = tallerRepository;
        this.tiendaRepository = tiendaRepository;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.userPrincipalId = userPrincipalId != null ? userPrincipalId.trim() : "";
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl.trim() : "https://api.sandbox.payouts.wompi.co/v2";
        this.publicKey = publicKey != null ? publicKey.trim() : "";
    }

    @Override
    public String modo() {
        return MODO;
    }

    @Override
    public boolean automatica() {
        return true;
    }

    @Override
    public ResultadoDispersion liquidar(RepartoPago reparto, SolicitudDispersion solicitud) {
        BigDecimal monto = Dinero.pesos(reparto.getMonto());
        if (monto.signum() <= 0) {
            throw new DispersionException("El monto a liquidar debe ser mayor que cero");
        }

        CuentaBancaria cuenta = obtenerCuentaBancaria(reparto);
        String referencia = "WOMPI-" + reparto.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (esModoApiReal()) {
            return ejecutarPayoutWompiApi(reparto, cuenta, referencia, monto);
        } else {
            return ejecutarPayoutSimulado(reparto, cuenta, referencia, monto);
        }
    }

    public boolean esModoApiReal() {
        return !apiKey.isBlank() && restTemplate != null;
    }

    public CuentaBancaria obtenerCuentaBancaria(RepartoPago reparto) {
        String tipo = reparto.getBeneficiarioTipo();
        String id = reparto.getBeneficiarioId();

        if (RepartoPago.TALLER.equals(tipo)) {
            Taller taller = tallerRepository.findById(id)
                    .orElseThrow(() -> new DispersionException("Taller beneficiario no encontrado (id: " + id + ")"));
            CuentaBancaria cb = taller.getCuentaBancaria();
            if (cb == null || cb.getNumeroCuenta() == null || cb.getNumeroCuenta().isBlank()) {
                throw new DispersionException("El taller '" + taller.getNombre() + "' no tiene configurada su cuenta bancaria para dispersión");
            }
            return cb;
        } else if (RepartoPago.TIENDA.equals(tipo)) {
            Tienda tienda = tiendaRepository.findById(id)
                    .orElseThrow(() -> new DispersionException("Tienda beneficiaria no encontrada (id: " + id + ")"));
            CuentaBancaria cb = tienda.getCuentaBancaria();
            if (cb == null || cb.getNumeroCuenta() == null || cb.getNumeroCuenta().isBlank()) {
                throw new DispersionException("La tienda '" + tienda.getNombre() + "' no tiene configurada su cuenta bancaria para dispersión");
            }
            return cb;
        } else {
            throw new DispersionException("Tipo de beneficiario no soportado para dispersión Wompi: " + tipo);
        }
    }

    private ResultadoDispersion ejecutarPayoutSimulado(RepartoPago reparto, CuentaBancaria cuenta, String referencia, BigDecimal monto) {
        log.info("Dispersión Wompi (Sandbox): Reparto #{} | Beneficiario: {} | Banco: {} ({}) | Cta: {} | Monto: {} COP",
                reparto.getId(), reparto.getBeneficiarioNombre(), cuenta.getBanco(), cuenta.getTipoCuenta(),
                cuenta.getNumeroCuenta(), monto);
        return new ResultadoDispersion(MODO, referencia, monto);
    }

    private ResultadoDispersion ejecutarPayoutWompiApi(RepartoPago reparto, CuentaBancaria cuenta, String referencia, BigDecimal monto) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            if (!userPrincipalId.isBlank()) {
                headers.set("user-principal-id", userPrincipalId);
            }
            headers.set("Idempotency-Key", referencia);

            Map<String, Object> body = new HashMap<>();
            body.put("amount_in_cents", monto.multiply(BigDecimal.valueOf(100)).longValue());
            body.put("currency", "COP");
            body.put("reference", referencia);
            body.put("notes", "DriveMaster Liquidacion linea " + reparto.getId());

            Map<String, Object> bankAccount = new HashMap<>();
            bankAccount.put("bank_code", cuenta.getBancoCodigo());
            bankAccount.put("account_type", cuenta.getTipoCuenta());
            bankAccount.put("account_number", cuenta.getNumeroCuenta());

            Map<String, Object> recipient = new HashMap<>();
            recipient.put("full_name", cuenta.getTitularNombre());
            recipient.put("legal_id_type", cuenta.getTitularDocumentoTipo());
            recipient.put("legal_id", cuenta.getTitularDocumentoNumero());
            recipient.put("bank_account", bankAccount);

            body.put("recipient", recipient);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = apiUrl.endsWith("/") ? apiUrl + "payouts" : apiUrl + "/payouts";

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> respBody = response.getBody();
                Object dataObj = respBody.get("data");
                String wompiId = null;
                if (dataObj instanceof Map<?, ?> dataMap) {
                    wompiId = String.valueOf(dataMap.get("id"));
                } else if (respBody.get("id") != null) {
                    wompiId = String.valueOf(respBody.get("id"));
                }
                String refFinal = wompiId != null ? referencia + " [ID:" + wompiId + "]" : referencia;
                log.info("Wompi Payout generado exitosamente: {} para reparto #{}", refFinal, reparto.getId());
                return new ResultadoDispersion(MODO, refFinal, monto);
            } else {
                throw new DispersionException("Respuesta no exitosa de Wompi Payouts: " + response.getStatusCode());
            }
        } catch (RestClientResponseException e) {
            String errorMsg = e.getResponseBodyAsString();
            log.error("Error respuesta Wompi Payouts (HTTP {}): {}", e.getStatusCode(), errorMsg);
            throw new DispersionException("Error en API de Wompi Payouts: " + e.getStatusCode() + " - " + errorMsg);
        } catch (Exception e) {
            if (e instanceof DispersionException de) throw de;
            log.error("Excepción invocando Wompi Payouts", e);
            throw new DispersionException("No se pudo conectar con la API de Wompi Payouts: " + e.getMessage());
        }
    }
}

