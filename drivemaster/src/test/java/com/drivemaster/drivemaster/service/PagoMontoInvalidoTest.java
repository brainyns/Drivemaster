package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wompi aprueba una transacción con un monto distinto al del link: el pago queda MONTO_INVALIDO, no se aplica, y
 * el reembolso y la alerta al ADMIN se registran una sola vez aunque el webhook y la redirección lleguen ambos.
 */
class PagoMontoInvalidoTest {

    private PagoRepository pagoRepository;
    private PagoMarketplaceService pagoMarketplace;
    private PagoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        pagoRepository = mock(PagoRepository.class);
        pagoMarketplace = mock(PagoMarketplaceService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        service = new PagoService(mock(SolicitudRepository.class), pagoRepository, mock(VentaService.class),
                mock(UsuarioRepository.class), mock(EmailsService.class), restTemplate, pagoMarketplace);
        ReflectionTestUtils.setField(service, "publicKey", "pub_test_abc");
        ReflectionTestUtils.setField(service, "apiUrl", "https://sandbox.wompi.co/v1");

        PagoEntity pago = new PagoEntity();
        pago.setId(7L);
        pago.setReferencia("DRV-REF1");
        pago.setConcepto(PagoMarketplaceService.COTIZACION);
        pago.setOrdenServicioId("o1");
        pago.setMonto(119_000.0);
        pago.setEstado("PENDIENTE");
        when(pagoRepository.findByReferencia("DRV-REF1")).thenReturn(Optional.of(pago));

        // Wompi dice APPROVED, pero por 100.000 COP en vez de 119.000
        Map<String, Object> txn = Map.of("id", "tx1", "reference", "DRV-REF1", "amount_in_cents", 10_000_000L,
                "currency", "COP", "status", "APPROVED");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", txn)));
    }

    @Test
    void registraElReembolsoYLaAlertaUnaSolaVez() {
        // La primera llamada marca el pago; la segunda ya no lo encuentra PENDIENTE
        when(pagoRepository.marcarMontoInvalido(7L, "tx1")).thenReturn(1).thenReturn(0);
        Map<String, Object> webhook = Map.of("event", "transaction.updated", "data", Map.of("transaction", Map.of("id", "tx1")));

        service.procesarWebhook(webhook);
        service.procesarWebhook(webhook);

        verify(pagoMarketplace, times(1)).montoInvalido(any(PagoEntity.class), eq(10_000_000L), eq("COP"));
        verify(pagoMarketplace, never()).confirmar(any(), anyString());
        verify(pagoRepository, never()).save(any());
    }
}
