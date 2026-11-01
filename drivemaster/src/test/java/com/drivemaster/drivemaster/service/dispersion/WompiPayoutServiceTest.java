package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.CuentaBancaria;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WompiPayoutServiceTest {

    private TallerRepository tallerRepository;
    private TiendaRepository tiendaRepository;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        tallerRepository = mock(TallerRepository.class);
        tiendaRepository = mock(TiendaRepository.class);
        restTemplate = mock(RestTemplate.class);
    }

    private CuentaBancaria cuentaEjemplo() {
        return CuentaBancaria.builder()
                .banco("Bancolombia")
                .bancoCodigo("1007")
                .tipoCuenta("AHORROS")
                .numeroCuenta("1234567890")
                .titularNombre("Taller Los Andes")
                .titularDocumentoTipo("NIT")
                .titularDocumentoNumero("900123456-1")
                .build();
    }

    @Test
    @DisplayName("Modo y automatica estan correctamente definidos")
    void configuracionBasica() {
        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc"
        );
        assertEquals("WOMPI", service.modo());
        assertFalse(service.automatica(), "cada pago lo aprueba el ADMIN");
        assertFalse(service.esModoApiReal());
        assertFalse(service.dineroReal());
    }

    @Test
    @DisplayName("A Wompi se le pide transferir el neto, en centavos, no el bruto")
    @SuppressWarnings("unchecked")
    void transfiereElNeto() {
        Map<String, Object> respuesta = Map.of("data", Map.of("payoutId", "pay-1"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(respuesta, HttpStatus.CREATED));
        RepartoPago neto = lineaDeTaller(cuentaEjemplo());
        neto.setMontoBruto(238_000.0);
        neto.setComisionRetenida(20_000.0);
        neto.setMonto(218_000.0);

        apiDePruebas().liquidar(neto, SolicitudDispersion.automatica());

        org.mockito.ArgumentCaptor<HttpEntity> pedido = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/payouts"), eq(HttpMethod.POST), pedido.capture(), any(ParameterizedTypeReference.class));
        Map<String, Object> cuerpo = (Map<String, Object>) pedido.getValue().getBody();
        Map<String, Object> transaccion = ((java.util.List<Map<String, Object>>) cuerpo.get("transactions")).get(0);
        assertEquals(21_800_000L, transaccion.get("amount"));
    }

    @Test
    @DisplayName("Rechaza monto cero o negativo")
    void rechazaMontoInvalido() {
        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc"
        );
        RepartoPago r0 = RepartoPago.builder().id(1L).monto(0.0).build();
        assertThrows(DispersionException.class, () -> service.liquidar(r0, SolicitudDispersion.automatica()));

        RepartoPago rNeg = RepartoPago.builder().id(1L).monto(-500.0).build();
        assertThrows(DispersionException.class, () -> service.liquidar(rNeg, SolicitudDispersion.automatica()));
    }

    @Test
    @DisplayName("Rechaza liquidación si el taller no tiene cuenta bancaria")
    void tallerSinCuentaBancaria() {
        Taller taller = Taller.builder().id("tal-1").nombre("Taller Sin Cuenta").cuentaBancaria(null).build();
        when(tallerRepository.findById("tal-1")).thenReturn(Optional.of(taller));

        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc"
        );

        RepartoPago reparto = RepartoPago.builder()
                .id(10L)
                .beneficiarioTipo(RepartoPago.TALLER)
                .beneficiarioId("tal-1")
                .beneficiarioNombre("Taller Sin Cuenta")
                .monto(150_000.0)
                .build();

        DispersionException ex = assertThrows(DispersionException.class,
                () -> service.liquidar(reparto, SolicitudDispersion.automatica()));
        assertTrue(ex.getMessage().contains("no tiene configurada su cuenta bancaria"));
    }

    @Test
    @DisplayName("Rechaza liquidación si la tienda no tiene cuenta bancaria")
    void tiendaSinCuentaBancaria() {
        Tienda tienda = Tienda.builder().id("tie-1").nombre("Repuestos Rapidos").cuentaBancaria(null).build();
        when(tiendaRepository.findById("tie-1")).thenReturn(Optional.of(tienda));

        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc"
        );

        RepartoPago reparto = RepartoPago.builder()
                .id(20L)
                .beneficiarioTipo(RepartoPago.TIENDA)
                .beneficiarioId("tie-1")
                .beneficiarioNombre("Repuestos Rapidos")
                .monto(80_000.0)
                .build();

        DispersionException ex = assertThrows(DispersionException.class,
                () -> service.liquidar(reparto, SolicitudDispersion.automatica()));
        assertTrue(ex.getMessage().contains("no tiene configurada su cuenta bancaria"));
    }

    @Test
    @DisplayName("Liquidación sandbox/simulada exitosa para Taller con cuenta bancaria")
    void liquidacionSimuladaTallerExitosa() {
        Taller taller = Taller.builder()
                .id("tal-2")
                .nombre("Taller Los Andes")
                .cuentaBancaria(cuentaEjemplo())
                .build();
        when(tallerRepository.findById("tal-2")).thenReturn(Optional.of(taller));

        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc"
        );

        RepartoPago reparto = RepartoPago.builder()
                .id(30L)
                .beneficiarioTipo(RepartoPago.TALLER)
                .beneficiarioId("tal-2")
                .beneficiarioNombre("Taller Los Andes")
                .monto(250_000.0)
                .build();

        ResultadoDispersion res = service.liquidar(reparto, SolicitudDispersion.automatica());
        assertNotNull(res);
        assertEquals("SIMULADO", res.modo(), "sin API key no salió dinero: no puede quedar como WOMPI");
        assertEquals(new BigDecimal("250000"), res.monto());
        assertTrue(res.referencia().startsWith("WOMPI-30-"));
    }

    @Test
    @DisplayName("Liquidación sandbox/simulada exitosa para Tienda con cuenta bancaria")
    void liquidacionSimuladaTiendaExitosa() {
        Tienda tienda = Tienda.builder()
                .id("tie-2")
                .nombre("Autopartes del Norte")
                .cuentaBancaria(cuentaEjemplo())
                .build();
        when(tiendaRepository.findById("tie-2")).thenReturn(Optional.of(tienda));

        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc"
        );

        RepartoPago reparto = RepartoPago.builder()
                .id(40L)
                .beneficiarioTipo(RepartoPago.TIENDA)
                .beneficiarioId("tie-2")
                .beneficiarioNombre("Autopartes del Norte")
                .monto(320_000.0)
                .build();

        ResultadoDispersion res = service.liquidar(reparto, SolicitudDispersion.automatica());
        assertNotNull(res);
        assertEquals("SIMULADO", res.modo());
        assertEquals(new BigDecimal("320000"), res.monto());
        assertTrue(res.referencia().startsWith("WOMPI-40-"));
    }

    @Test
    @DisplayName("Liquidación API real Wompi Payouts exitosa")
    void liquidacionApiRealExitosa() {
        Taller taller = Taller.builder()
                .id("tal-3")
                .nombre("Taller Central")
                .cuentaBancaria(cuentaEjemplo())
                .build();
        when(tallerRepository.findById("tal-3")).thenReturn(Optional.of(taller));

        Map<String, Object> respBody = Map.of(
                "data", Map.of("id", "pout_12345", "status", "PENDING")
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(respBody, HttpStatus.CREATED));

        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate,
                "sec_test_api_key", "principal-1", "https://api.sandbox.payouts.wompi.co/v1", "pub_test_key", "acc_test_123"
        );
        assertTrue(service.esModoApiReal());

        RepartoPago reparto = RepartoPago.builder()
                .id(50L)
                .beneficiarioTipo(RepartoPago.TALLER)
                .beneficiarioId("tal-3")
                .beneficiarioNombre("Taller Central")
                .monto(500_000.0)
                .build();

        ResultadoDispersion res = service.liquidar(reparto, SolicitudDispersion.automatica());
        assertNotNull(res);
        assertEquals("WOMPI", res.modo());
        assertEquals(new BigDecimal("500000"), res.monto());
        assertTrue(res.referencia().contains("ID:pout_12345"));
    }

    @Test
    @DisplayName("Liquidación API real Wompi Payouts lanza DispersionException ante fallo HTTP")
    void liquidacionApiRealFalloHttp() {
        Taller taller = Taller.builder()
                .id("tal-4")
                .nombre("Taller Falla")
                .cuentaBancaria(cuentaEjemplo())
                .build();
        when(tallerRepository.findById("tal-4")).thenReturn(Optional.of(taller));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientResponseException("Fondos insuficientes", 400, "Bad Request", null, "{\"error\": \"INSUFFICIENT_FUNDS\"}".getBytes(), null));

        WompiPayoutService service = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate,
                "sec_test_api_key", "principal-1", "https://api.sandbox.payouts.wompi.co/v1", "pub_test_key", "acc_test_123"
        );

        RepartoPago reparto = RepartoPago.builder()
                .id(60L)
                .beneficiarioTipo(RepartoPago.TALLER)
                .beneficiarioId("tal-4")
                .beneficiarioNombre("Taller Falla")
                .monto(500_000.0)
                .build();

        DispersionException ex = assertThrows(DispersionException.class,
                () -> service.liquidar(reparto, SolicitudDispersion.automatica()));
        assertTrue(ex.getMessage().contains("INSUFFICIENT_FUNDS"));
        assertFalse(ex instanceof DispersionInciertaException, "un 4xx es definitivo: se puede reintentar");
    }

    // ─── Nunca marcar pagado lo que no salió ni adivinar a dónde va el dinero ──

    private RepartoPago lineaDeTaller(CuentaBancaria cuenta) {
        when(tallerRepository.findById("tal-9")).thenReturn(Optional.of(
                Taller.builder().id("tal-9").nombre("Taller Nueve").cuentaBancaria(cuenta).build()));
        return RepartoPago.builder().id(90L).beneficiarioTipo(RepartoPago.TALLER).beneficiarioId("tal-9")
                .beneficiarioNombre("Taller Nueve").monto(100_000.0).build();
    }

    private WompiPayoutService apiDePruebas() {
        return new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate,
                "sec_test_api_key", "principal-1", "https://api.sandbox.payouts.wompi.co/v1", "pub_test_key", "acc_test_123");
    }

    // ─── Consulta del intento anterior ───────────────────────────────────────

    private static RepartoPago conReferencia(String referencia) {
        return RepartoPago.builder().id(90L).referenciaLiquidacion(referencia).build();
    }

    @SuppressWarnings("unchecked")
    private void wompiResponde(Object data) {
        Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("data", data);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(cuerpo, HttpStatus.OK));
    }

    /** Como responde el sandbox: data es una página con records, y cada transacción no trae el id de su lote. */
    private static Map<String, Object> pagina(Map<?, ?>... transacciones) {
        return Map.of("page", 1, "limit", 10, "total", transacciones.length, "pages", 1, "records", List.of(transacciones));
    }

    private static Map<String, Object> transaccion(String estado) {
        Map<String, Object> t = new java.util.HashMap<>(Map.of("id", "txn-1", "amountInCents", 6_867_000, "status", estado, "reference", "WOMPI-90-AB"));
        t.put("failureReason", null);
        return t;
    }

    private PagoAnterior.Estado estadoCon(Object data) {
        wompiResponde(data);
        return apiDePruebas().consultarPagoAnterior(conReferencia("WOMPI-90-AB [ID:pay-1]")).estado();
    }

    @Test
    @DisplayName("Con id consulta las transacciones del pago y no envía nada")
    @SuppressWarnings("unchecked")
    void consultaPorId() {
        wompiResponde(pagina(transaccion("APPROVED")));

        PagoAnterior p = apiDePruebas().consultarPagoAnterior(conReferencia("WOMPI-90-AB [ID:pay-1]"));

        assertEquals(new PagoAnterior(PagoAnterior.Estado.SALIO, "pay-1"), p);
        org.mockito.ArgumentCaptor<URI> uri = org.mockito.ArgumentCaptor.forClass(URI.class);
        org.mockito.ArgumentCaptor<HttpEntity> pedido = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uri.capture(), eq(HttpMethod.GET), pedido.capture(), any(ParameterizedTypeReference.class));
        assertEquals("https://api.sandbox.payouts.wompi.co/v1/payouts/pay-1/transactions", uri.getValue().toString());
        assertEquals("sec_test_api_key", pedido.getValue().getHeaders().getFirst("x-api-key"));
        verificarSinPago();
    }

    @Test
    @DisplayName("Sin id consulta por referencia y queda el id de la transacción, porque no trae el del lote")
    @SuppressWarnings("unchecked")
    void consultaPorReferencia() {
        wompiResponde(pagina(transaccion("APPROVED")));

        PagoAnterior p = apiDePruebas().consultarPagoAnterior(conReferencia("WOMPI-90-AB"));

        assertEquals(new PagoAnterior(PagoAnterior.Estado.SALIO, "txn-1"), p);
        org.mockito.ArgumentCaptor<URI> uri = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).exchange(uri.capture(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertEquals("https://api.sandbox.payouts.wompi.co/v1/transactions/WOMPI-90-AB", uri.getValue().toString());
    }

    @Test
    @DisplayName("Solo los estados finales de no pago dicen que no salió")
    void estadosDeNoPago() {
        for (String estado : List.of("FAILED", "CANCELLED", "REJECTED")) {
            assertEquals(PagoAnterior.Estado.NO_SALIO, estadoCon(pagina(transaccion(estado))), estado);
        }
    }

    @Test
    @DisplayName("Un pago en curso o un estado desconocido no permiten reintentar")
    void estadosSinDecidir() {
        for (String estado : List.of("PENDING", "PROCESSING", "READY_TO_FILE", "OTRO")) {
            assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(pagina(transaccion(estado))), estado);
        }
    }

    @Test
    @DisplayName("Un estado final junto a uno en curso no permite reintentar; uno aprobado manda")
    void variasTransacciones() {
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(pagina(transaccion("FAILED"), transaccion("PENDING"))));
        assertEquals(PagoAnterior.Estado.SALIO, estadoCon(pagina(transaccion("FAILED"), transaccion("APPROVED"))));
    }

    @Test
    @DisplayName("El estado del lote no cuenta: TOTAL_PAYMENT no dice que se pagó")
    void elLoteNoEsLaTransaccion() {
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(Map.of("id", "pay-1", "status", "TOTAL_PAYMENT")));
    }

    @Test
    @DisplayName("La transacción de otra referencia no dice nada de este pago")
    void otraReferencia() {
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(pagina(Map.of("status", "APPROVED", "reference", "WOMPI-91-ZZ"))));
    }

    @Test
    @DisplayName("Sin registros o con una respuesta desconocida no se sabe")
    void respuestaSinDatos() {
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(pagina()));
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(Map.of("otro", 1)));
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(null));
    }

    @Test
    @DisplayName("Una respuesta con otra forma no cuenta, aunque diga APPROVED")
    void otrasFormasDeRespuesta() {
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(List.of(transaccion("APPROVED"))));
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(transaccion("APPROVED")));
    }

    @Test
    @DisplayName("Una transacción sin referencia no se da por propia")
    void sinReferencia() {
        assertEquals(PagoAnterior.Estado.SIN_SABER, estadoCon(pagina(Map.of("id", "txn-1", "status", "APPROVED"))));
    }

    @Test
    @DisplayName("Si la consulta falla, incluso con 404, no se sabe")
    @SuppressWarnings("unchecked")
    void consultaFallida() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("sin red"))
                .thenThrow(org.springframework.web.client.HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertEquals(PagoAnterior.SIN_SABER, apiDePruebas().consultarPagoAnterior(conReferencia("WOMPI-90-AB")));
        assertEquals(PagoAnterior.SIN_SABER, apiDePruebas().consultarPagoAnterior(conReferencia("WOMPI-90-AB")));
    }

    @Test
    @DisplayName("Sin API key no consulta")
    void sinApiKey() {
        WompiPayoutService sinLlave = new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate, "", "", "", "pub_test_abc");

        assertEquals(PagoAnterior.SIN_SABER, sinLlave.consultarPagoAnterior(conReferencia("WOMPI-90-AB")));
        verifyNoInteractions(restTemplate);
    }

    private void verificarSinPago() {
        verify(restTemplate, never()).exchange(contains("/payouts"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("Sin API key y con llaves reales rechaza el pago en vez de simularlo")
    void sinApiKeyConLlavesRealesRechaza() {
        WompiPayoutService service = new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate,
                "", "", "https://api.payouts.wompi.co/v1", "pub_prod_abc");
        RepartoPago linea = lineaDeTaller(cuentaEjemplo());

        DispersionException ex = assertThrows(DispersionException.class, () -> service.liquidar(linea, SolicitudDispersion.automatica()));
        assertTrue(ex.getMessage().contains("WOMPI_PAYOUTS_API_KEY"));
    }

    @Test
    @DisplayName("Rechaza llaves reales con el sandbox y llaves de pruebas con producción")
    void configuracionMezcladaRechaza() {
        RepartoPago linea = lineaDeTaller(cuentaEjemplo());
        WompiPayoutService realEnSandbox = new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate,
                "prv_key", "", "https://api.sandbox.payouts.wompi.co/v1", "pub_prod_abc", "acc");
        WompiPayoutService pruebaEnProduccion = new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate,
                "prv_key", "", "https://api.payouts.wompi.co/v1", "pub_test_abc", "acc");

        assertThrows(DispersionException.class, () -> realEnSandbox.liquidar(linea, SolicitudDispersion.automatica()));
        assertThrows(DispersionException.class, () -> pruebaEnProduccion.liquidar(linea, SolicitudDispersion.automatica()));
        verificarSinPago();
    }

    @Test
    @DisplayName("Solo producción con API key cuenta como dinero real")
    void dineroRealSoloEnProduccion() {
        assertFalse(apiDePruebas().dineroReal());
        assertTrue(new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate,
                "prv_key", "", "https://api.payouts.wompi.co/v1", "pub_prod_abc", "acc").dineroReal());
    }

    @Test
    @DisplayName("Un banco desconocido se rechaza: nunca cae en Bancolombia")
    void bancoDesconocidoNoCaeEnBancolombia() {
        CuentaBancaria cuenta = cuentaEjemplo();
        cuenta.setBanco("Banco Inventado");
        cuenta.setBancoCodigo("9999");
        RepartoPago linea = lineaDeTaller(cuenta);

        DispersionException ex = assertThrows(DispersionException.class, () -> apiDePruebas().liquidar(linea, SolicitudDispersion.automatica()));
        assertTrue(ex.getMessage().contains("Banco Inventado"));
        verificarSinPago();
    }

    @Test
    @DisplayName("Sin documento o nombre del titular no sale el pago")
    void titularIncompletoNoSale() {
        CuentaBancaria sinDocumento = cuentaEjemplo();
        sinDocumento.setTitularDocumentoNumero(null);
        RepartoPago linea = lineaDeTaller(sinDocumento);
        assertThrows(DispersionException.class, () -> apiDePruebas().liquidar(linea, SolicitudDispersion.automatica()));

        CuentaBancaria sinNombre = cuentaEjemplo();
        sinNombre.setTitularNombre(" ");
        RepartoPago otra = lineaDeTaller(sinNombre);
        assertThrows(DispersionException.class, () -> apiDePruebas().liquidar(otra, SolicitudDispersion.automatica()));
        verificarSinPago();
    }

    @Test
    @DisplayName("Sin cuenta de origen configurada ni detectable no usa una cuenta fija")
    void sinCuentaOrigenRechaza() {
        when(restTemplate.exchange(contains("/accounts"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("sin red"));
        WompiPayoutService service = new WompiPayoutService(tallerRepository, tiendaRepository, restTemplate,
                "sec_test_api_key", "", "https://api.sandbox.payouts.wompi.co/v1", "pub_test_key");
        RepartoPago linea = lineaDeTaller(cuentaEjemplo());

        DispersionException ex = assertThrows(DispersionException.class, () -> service.liquidar(linea, SolicitudDispersion.automatica()));
        assertTrue(ex.getMessage().contains("WOMPI_PAYOUTS_ACCOUNT_ID"));
        assertFalse(ex instanceof DispersionInciertaException, "no salió ningún pago");
        verificarSinPago();
    }

    @Test
    @DisplayName("Tiempo vencido o 5xx en el POST: el pago queda incierto, no fallido")
    void tiempoVencidoO5xxEsIncierto() {
        RepartoPago linea = lineaDeTaller(cuentaEjemplo());
        when(restTemplate.exchange(contains("/payouts"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("Read timed out"))
                .thenThrow(new RestClientResponseException("Error", 503, "Service Unavailable", null, new byte[0], null));

        DispersionInciertaException porTiempo = assertThrows(DispersionInciertaException.class,
                () -> apiDePruebas().liquidar(linea, SolicitudDispersion.automatica()));
        assertTrue(porTiempo.getReferencia().startsWith("WOMPI-90-"));
        assertThrows(DispersionInciertaException.class, () -> apiDePruebas().liquidar(linea, SolicitudDispersion.automatica()));
    }

    @Test
    @DisplayName("El número de cuenta se enmascara y las llamadas a Wompi tienen límite de tiempo")
    void enmascaraYTieneTimeouts() {
        assertEquals("****7890", WompiPayoutService.enmascarar("1234567890"));
        assertEquals("****", WompiPayoutService.enmascarar("12"));
        assertEquals("****", WompiPayoutService.enmascarar(null));

        var fabrica = new com.drivemaster.drivemaster.config.RestTemplateConfig().wompiPayoutsRestTemplate().getRequestFactory();
        assertEquals(com.drivemaster.drivemaster.config.RestTemplateConfig.PAYOUTS_CONNECT_TIMEOUT_MS,
                org.springframework.test.util.ReflectionTestUtils.getField(fabrica, "connectTimeout"));
        assertEquals(com.drivemaster.drivemaster.config.RestTemplateConfig.PAYOUTS_READ_TIMEOUT_MS,
                org.springframework.test.util.ReflectionTestUtils.getField(fabrica, "readTimeout"));
    }
}

