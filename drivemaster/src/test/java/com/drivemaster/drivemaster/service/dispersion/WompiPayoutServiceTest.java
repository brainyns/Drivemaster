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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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
        assertTrue(service.automatica());
        assertFalse(service.esModoApiReal());
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
        assertEquals("WOMPI", res.modo());
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
        assertEquals("WOMPI", res.modo());
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
                "sec_test_api_key", "principal-1", "https://api.sandbox.payouts.wompi.co/v2", "pub_test_key"
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
                "sec_test_api_key", "principal-1", "https://api.sandbox.payouts.wompi.co/v2", "pub_test_key"
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
    }
}

