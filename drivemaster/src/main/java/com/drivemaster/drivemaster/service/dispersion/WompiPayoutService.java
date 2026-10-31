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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de dispersión automática de liquidaciones a través de Wompi Payouts (Pagos a Terceros).
 * Transfiere a cuentas bancarias registradas de talleres y tiendas.
 * Si no hay API key configurada o se usan llaves de prueba sin API key de payouts,
 * opera en modo sandbox/simulado con validación estricta de cuentas bancarias.
 */
public class WompiPayoutService implements DispersionService {

    private static final Logger log = LoggerFactory.getLogger(WompiPayoutService.class);
    public static final String MODO = "WOMPI";
    public static final String DEFAULT_ACCOUNT_ID = "80ef0b70-df41-4e42-9b21-66d2d17a17df";

    private final TallerRepository tallerRepository;
    private final TiendaRepository tiendaRepository;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String userPrincipalId;
    private final String apiUrl;
    private final String publicKey;
    private volatile String accountId;

    /**
     * Caché en memoria para mapear códigos ACH / nombres a los UUIDs de bancos exigidos por Wompi Payouts.
     */
    private final Map<String, String> bankUuidCache = new ConcurrentHashMap<>();

    private static final Map<String, String> BANCOS_CONOCIDOS_UUID = Map.ofEntries(
            Map.entry("1007", "fe0dc841-b057-4349-a6dc-a88d29d2605a"), // Bancolombia
            Map.entry("BANCOLOMBIA", "fe0dc841-b057-4349-a6dc-a88d29d2605a"),
            Map.entry("1001", "ab8a9954-0f01-41fa-bc0f-d12862806df2"), // Banco de Bogotá
            Map.entry("BANCO_BOGOTA", "ab8a9954-0f01-41fa-bc0f-d12862806df2"),
            Map.entry("1051", "b38b9c92-e44d-4831-b74f-e3a517c75b4e"), // Davivienda
            Map.entry("BANCO_DAVIVIENDA", "b38b9c92-e44d-4831-b74f-e3a517c75b4e"),
            Map.entry("1013", "9183a03b-cd82-451a-a6c7-6a9ab406a477"), // BBVA
            Map.entry("BBVA", "9183a03b-cd82-451a-a6c7-6a9ab406a477"),
            Map.entry("1023", "eac87f23-3aa1-4d62-97ce-b27186160619"), // Banco de Occidente
            Map.entry("BANCO_OCCIDENTE", "eac87f23-3aa1-4d62-97ce-b27186160619"),
            Map.entry("1002", "35ad5684-f9b1-4f12-be25-0a42e7e86fd6"), // Banco Popular
            Map.entry("BANCO_POPULAR", "35ad5684-f9b1-4f12-be25-0a42e7e86fd6"),
            Map.entry("1032", "09929648-ce86-4689-9ac8-dc60cd07a657"), // Banco Caja Social
            Map.entry("BANCO_CAJA_SOCIAL", "09929648-ce86-4689-9ac8-dc60cd07a657"),
            Map.entry("1019", "b8b11d82-8ae4-4d4a-bc51-8db066c9aa9f"), // Scotiabank Colpatria
            Map.entry("SCOTIABANK_COLPATRIA", "b8b11d82-8ae4-4d4a-bc51-8db066c9aa9f"),
            Map.entry("1006", "5ee784de-743e-4169-9e39-3f9a7aeeba0a"), // Itaú
            Map.entry("ITAU", "5ee784de-743e-4169-9e39-3f9a7aeeba0a"),
            Map.entry("1507", "6e422ee9-6863-4882-a265-42258b410caa"), // Nequi
            Map.entry("NEQUI", "6e422ee9-6863-4882-a265-42258b410caa"),
            Map.entry("1551", "4b636ca6-3808-4428-89a4-48ab2da42bd0"), // Daviplata
            Map.entry("DAVIPLATA", "4b636ca6-3808-4428-89a4-48ab2da42bd0"),
            Map.entry("1070", "cc61df83-0098-41cc-825e-076f20b45a43"), // Lulo Bank
            Map.entry("LULO_BANK", "cc61df83-0098-41cc-825e-076f20b45a43"),
            Map.entry("1809", "942a4163-505a-40c4-bb21-cdb10c80029a"), // Nu Bank
            Map.entry("NU_BANK", "942a4163-505a-40c4-bb21-cdb10c80029a"),
            Map.entry("1811", "9330c6a9-cde9-4969-b890-08d02752ba8a"), // RappiPay
            Map.entry("RAPPIPAY", "9330c6a9-cde9-4969-b890-08d02752ba8a"),
            Map.entry("1052", "33618602-f875-4a8c-ba3c-e7f76055b417"), // AV Villas
            Map.entry("BANCO_AV_VILLAS", "33618602-f875-4a8c-ba3c-e7f76055b417"),
            Map.entry("1040", "336bf87e-05ce-410c-b3c8-509d2966b7d0"), // Banco Agrario
            Map.entry("BANCO_AGRARIO", "336bf87e-05ce-410c-b3c8-509d2966b7d0"),
            Map.entry("1062", "c4ab94ac-bdb0-4763-945b-6383893d7ad5"), // Banco Falabella
            Map.entry("BANCO_FALABELLA", "c4ab94ac-bdb0-4763-945b-6383893d7ad5"),
            Map.entry("1063", "0fed6be0-51a3-4a36-a08c-a6051f058c52"), // Finandina
            Map.entry("BANCO_FINANDINA", "0fed6be0-51a3-4a36-a08c-a6051f058c52"),
            Map.entry("1060", "6a1aa078-5f9a-4275-b37b-b1d18f47b563"), // Pichincha
            Map.entry("BANCO_PICHINCHA", "6a1aa078-5f9a-4275-b37b-b1d18f47b563"),
            Map.entry("1069", "8e8a3a0d-e9c4-470a-87bf-677c2f700992"), // Serfinanza
            Map.entry("BANCO_SERFINANZA", "8e8a3a0d-e9c4-470a-87bf-677c2f700992"),
            Map.entry("1061", "54f52c3e-e09d-4993-af73-a529520843bd"), // Bancoomeva
            Map.entry("BANCOOMEVA", "54f52c3e-e09d-4993-af73-a529520843bd"),
            Map.entry("1058", "28c0640f-4a90-4295-b317-b718012fc427"), // Procredit
            Map.entry("BANCO_PROCREDIT", "28c0640f-4a90-4295-b317-b718012fc427"),
            Map.entry("1065", "9c2a20bc-bf5a-4a03-845f-f2c78e0221d8"), // Santander
            Map.entry("BANCO_SANTANDER", "9c2a20bc-bf5a-4a03-845f-f2c78e0221d8"),
            Map.entry("1066", "c98eac7b-9185-4f8d-bd9c-c6a3a5c26169"), // Coopcentral
            Map.entry("BANCO_COOPERATIVO_COOPCENTRAL", "c98eac7b-9185-4f8d-bd9c-c6a3a5c26169"),
            Map.entry("1801", "75e0b723-f52a-4470-ac2c-2a744028f0e1"), // Movii
            Map.entry("MOVII", "75e0b723-f52a-4470-ac2c-2a744028f0e1"),
            Map.entry("1804", "85fb8d01-d93b-4eec-a007-2c641bccba47"), // Ualá
            Map.entry("UALA", "85fb8d01-d93b-4eec-a007-2c641bccba47"),
            Map.entry("1812", "dd90fd84-3834-4cb3-adab-90e9c1a20ce3"), // Coink
            Map.entry("COINK", "dd90fd84-3834-4cb3-adab-90e9c1a20ce3"),
            Map.entry("1808", "62ead327-ac39-4612-a8c1-afae74cd50a9"), // Bold
            Map.entry("BOLD", "62ead327-ac39-4612-a8c1-afae74cd50a9")
    );

    public WompiPayoutService(TallerRepository tallerRepository,
                              TiendaRepository tiendaRepository,
                              RestTemplate restTemplate,
                              String apiKey,
                              String userPrincipalId,
                              String apiUrl,
                              String publicKey) {
        this(tallerRepository, tiendaRepository, restTemplate, apiKey, userPrincipalId, apiUrl, publicKey, null);
    }

    public WompiPayoutService(TallerRepository tallerRepository,
                              TiendaRepository tiendaRepository,
                              RestTemplate restTemplate,
                              String apiKey,
                              String userPrincipalId,
                              String apiUrl,
                              String publicKey,
                              String accountId) {
        this.tallerRepository = tallerRepository;
        this.tiendaRepository = tiendaRepository;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.userPrincipalId = userPrincipalId != null ? userPrincipalId.trim() : "";
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl.trim() : "https://api.sandbox.payouts.wompi.co/v1";
        this.publicKey = publicKey != null ? publicKey.trim() : "";
        this.accountId = accountId != null && !accountId.isBlank() ? accountId.trim() : null;
        this.bankUuidCache.putAll(BANCOS_CONOCIDOS_UUID);
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
            headers.set("idempotency-key", referencia);

            String targetAccountId = resolverAccountId();
            String targetBankId = resolverBankId(cuenta);
            String emailBeneficiario = obtenerEmailBeneficiario(reparto);
            String cleanLegalId = cuenta.getTitularDocumentoNumero() != null
                    ? cuenta.getTitularDocumentoNumero().replaceAll("[^0-9]", "")
                    : "123456789";
            String legalIdType = "PASAPORTE".equalsIgnoreCase(cuenta.getTitularDocumentoTipo()) ? "PP" : cuenta.getTitularDocumentoTipo();

            Map<String, Object> transaction = new HashMap<>();
            transaction.put("legalIdType", legalIdType != null && !legalIdType.isBlank() ? legalIdType : "CC");
            transaction.put("legalId", cleanLegalId);
            transaction.put("bankId", targetBankId);
            transaction.put("accountType", cuenta.getTipoCuenta() != null ? cuenta.getTipoCuenta() : "AHORROS");
            transaction.put("accountNumber", cuenta.getNumeroCuenta());
            transaction.put("name", cuenta.getTitularNombre());
            transaction.put("email", emailBeneficiario);
            transaction.put("amount", monto.multiply(BigDecimal.valueOf(100)).longValue());
            transaction.put("reference", referencia);

            Map<String, Object> body = new HashMap<>();
            body.put("reference", referencia);
            body.put("accountId", targetAccountId);
            body.put("paymentType", "PROVIDERS");
            body.put("transactions", Collections.singletonList(transaction));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = apiUrl.endsWith("/") ? apiUrl + "payouts" : apiUrl + "/payouts";

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {}
            );

            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> respBody = response.getBody();
                Object dataObj = respBody.get("data");
                String wompiId = null;
                Integer failedCount = null;

                if (dataObj instanceof Map<?, ?> dataMap) {
                    wompiId = dataMap.get("payoutId") != null ? String.valueOf(dataMap.get("payoutId"))
                            : (dataMap.get("id") != null ? String.valueOf(dataMap.get("id")) : null);
                    if (dataMap.get("failed") instanceof Number n) {
                        failedCount = n.intValue();
                    }
                } else if (respBody.get("id") != null) {
                    wompiId = String.valueOf(respBody.get("id"));
                }

                if (failedCount != null && failedCount > 0) {
                    throw new DispersionException("Wompi Payout reportó transacciones fallidas en el lote");
                }

                String refFinal = wompiId != null ? referencia + " [ID:" + wompiId + "]" : referencia;
                log.info("Wompi Payout generado exitosamente: {} para reparto #{}", refFinal, reparto.getId());
                return new ResultadoDispersion(MODO, refFinal, monto);
            } else {
                throw new DispersionException("Respuesta no exitosa de Wompi Payouts");
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

    private String obtenerEmailBeneficiario(RepartoPago reparto) {
        try {
            if (RepartoPago.TIENDA.equals(reparto.getBeneficiarioTipo())) {
                Tienda ti = tiendaRepository.findById(reparto.getBeneficiarioId()).orElse(null);
                if (ti != null && ti.getCorreoContacto() != null && !ti.getCorreoContacto().isBlank()) {
                    return ti.getCorreoContacto().trim();
                }
            }
        } catch (Exception ignored) {}
        return "admin@drivemaster.com";
    }

    public synchronized String resolverAccountId() {
        if (accountId != null && !accountId.isBlank()) {
            return accountId;
        }
        if (restTemplate == null || apiKey.isBlank()) {
            return DEFAULT_ACCOUNT_ID;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            if (!userPrincipalId.isBlank()) {
                headers.set("user-principal-id", userPrincipalId);
            }
            HttpEntity<Void> req = new HttpEntity<>(headers);
            String url = apiUrl.endsWith("/") ? apiUrl + "accounts" : apiUrl + "/accounts";
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, req, new ParameterizedTypeReference<>() {}
            );
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object data = resp.getBody().get("data");
                if (data instanceof List<?> list && !list.isEmpty()) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            if (Boolean.TRUE.equals(map.get("isMain"))) {
                                this.accountId = String.valueOf(map.get("id"));
                                return this.accountId;
                            }
                        }
                    }
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> map && map.get("id") != null) {
                        this.accountId = String.valueOf(map.get("id"));
                        return this.accountId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo consultar automáticamente la cuenta origen en Wompi Payouts: {}", e.getMessage());
        }
        return DEFAULT_ACCOUNT_ID;
    }

    public String resolverBankId(CuentaBancaria cuenta) {
        String codigo = cuenta.getBancoCodigo() != null ? cuenta.getBancoCodigo().trim() : "";
        if (codigo.matches("^[0-9a-fA-F\\-]{36}$")) {
            return codigo;
        }

        if (bankUuidCache.containsKey(codigo)) {
            return bankUuidCache.get(codigo);
        }

        String nombre = cuenta.getBanco() != null ? cuenta.getBanco().trim().toUpperCase() : "";
        if (bankUuidCache.containsKey(nombre)) {
            return bankUuidCache.get(nombre);
        }

        // Consultar /banks si tenemos conexión con la API
        if (restTemplate != null && !apiKey.isBlank()) {
            cargarCatalogoBancos();
            if (bankUuidCache.containsKey(codigo)) return bankUuidCache.get(codigo);
            if (bankUuidCache.containsKey(nombre)) return bankUuidCache.get(nombre);
        }

        // Si no se encuentra en el catálogo, Bancolombia por defecto o el código mismo
        return BANCOS_CONOCIDOS_UUID.getOrDefault(codigo, "fe0dc841-b057-4349-a6dc-a88d29d2605a");
    }

    private synchronized void cargarCatalogoBancos() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            if (!userPrincipalId.isBlank()) {
                headers.set("user-principal-id", userPrincipalId);
            }
            HttpEntity<Void> req = new HttpEntity<>(headers);
            String url = apiUrl.endsWith("/") ? apiUrl + "banks" : apiUrl + "/banks";
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, req, new ParameterizedTypeReference<>() {}
            );
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object data = resp.getBody().get("data");
                if (data instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> b) {
                            String id = String.valueOf(b.get("id"));
                            if (b.get("achCode") != null) bankUuidCache.put(String.valueOf(b.get("achCode")), id);
                            if (b.get("code") != null) bankUuidCache.put(String.valueOf(b.get("code")), id);
                            if (b.get("name") != null) bankUuidCache.put(String.valueOf(b.get("name")).toUpperCase(), id);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar el catálogo de bancos desde Wompi Payouts: {}", e.getMessage());
        }
    }
}
