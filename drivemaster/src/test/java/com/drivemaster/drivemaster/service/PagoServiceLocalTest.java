package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pago en local: sin redirect-url cuando apunta a localhost (Wompi responde 403) y
 * pago simulado solo en desarrollo, con el mismo código de confirmación.
 */
class PagoServiceLocalTest {

    private PagoRepository pagoRepository;
    private UsuarioRepository usuarioRepository;
    private PagoMarketplaceService pagoMarketplace;
    private PagoService service;
    private PagoEntity pago;

    @BeforeEach
    void setUp() {
        pagoRepository = mock(PagoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        pagoMarketplace = mock(PagoMarketplaceService.class);
        service = new PagoService(mock(SolicitudRepository.class), pagoRepository, mock(VentaService.class),
                usuarioRepository, mock(EmailsService.class), mock(RestTemplate.class), pagoMarketplace);
        ReflectionTestUtils.setField(service, "publicKey", "pub_test_abc");
        ReflectionTestUtils.setField(service, "integrityKey", "test_integrity_x");
        ReflectionTestUtils.setField(service, "checkoutUrl", "https://checkout.wompi.co/p/");
        ReflectionTestUtils.setField(service, "redirectUrlDefault", "http://localhost:8080/api/pagos/confirmar-redirect");

        when(usuarioRepository.findByCorreo("cliente@demo.com"))
                .thenReturn(Optional.of(Usuario.builder().id("u1").correo("cliente@demo.com").build()));
        pago = new PagoEntity();
        pago.setId(7L);
        pago.setReferencia("DRV-REF1");
        pago.setConcepto(PagoMarketplaceService.COTIZACION);
        pago.setOrdenServicioId("o1");
        pago.setUsuarioId("u1");
        pago.setMonto(119_000.0);
        pago.setEstado("PENDIENTE");
        when(pagoRepository.findByReferencia("DRV-REF1")).thenReturn(Optional.of(pago));
    }

    @Test
    void detectaUrlsLocales() {
        assertTrue(PagoService.esUrlLocal("http://localhost:8080/api/pagos/confirmar-redirect"));
        assertTrue(PagoService.esUrlLocal("http://127.0.0.1:5173/pago-resultado"));
        assertFalse(PagoService.esUrlLocal("https://drivemaster.onrender.com/api/pagos/confirmar-redirect"));
        assertFalse(PagoService.esUrlLocal("no es una url"));
    }

    @Test
    void enLocalElEnlaceNoLlevaRedirectUrl() {
        ReflectionTestUtils.setField(service, "verificacionManualHabilitada", true);
        Map<String, Object> r = service.crearPagoMarketplace(PagoMarketplaceService.DIAGNOSTICO, "u1", 50_000, "o1", null);
        assertFalse(r.get("checkoutUrl").toString().contains("redirect-url"));
        assertEquals(true, r.get("verificacionManual"));
    }

    @Test
    void sinElFlagNoSeOfreceLaVerificacionManualAunqueSeaLocal() {
        // Apagado por defecto: aunque la redirección sea localhost, el botón no se ofrece
        Map<String, Object> r = service.crearPagoMarketplace(PagoMarketplaceService.DIAGNOSTICO, "u1", 50_000, "o1", null);
        assertFalse(r.get("checkoutUrl").toString().contains("redirect-url"));
        assertEquals(false, r.get("verificacionManual"));
        assertFalse(service.verificacionManualDisponible());
    }

    @Test
    void laVerificacionManualExigeLlavesDePrueba() {
        ReflectionTestUtils.setField(service, "verificacionManualHabilitada", true);
        ReflectionTestUtils.setField(service, "publicKey", "pub_prod_abc");
        assertFalse(service.verificacionManualDisponible());
        Map<String, Object> r = service.crearPagoMarketplace(PagoMarketplaceService.DIAGNOSTICO, "u1", 50_000, "o1", null);
        assertEquals(false, r.get("verificacionManual"));
    }

    @Test
    void desplegadoLaVerificacionManualNuncaSeOfreceAunConElFlag() {
        ReflectionTestUtils.setField(service, "verificacionManualHabilitada", true);
        ReflectionTestUtils.setField(service, "redirectUrlDefault", "https://api.drivemaster.co/api/pagos/confirmar-redirect");
        assertFalse(service.verificacionManualDisponible());
    }

    @Test
    void desplegadoElEnlaceLlevaRedirectUrl() {
        ReflectionTestUtils.setField(service, "redirectUrlDefault", "https://api.drivemaster.co/api/pagos/confirmar-redirect");
        Map<String, Object> r = service.crearPagoMarketplace(PagoMarketplaceService.DIAGNOSTICO, "u1", 50_000, "o1", null);
        assertTrue(r.get("checkoutUrl").toString().contains("redirect-url="));
        assertEquals(false, r.get("verificacionManual"));
    }

    @Test
    void pagoSimuladoApagadoPorDefecto() {
        assertFalse(service.pagoSimuladoDisponible());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.simularPagoAprobado("DRV-REF1", "cliente@demo.com"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verifyNoInteractions(pagoMarketplace);
    }

    @Test
    void pagoSimuladoExigeLlavesDePrueba() {
        ReflectionTestUtils.setField(service, "pagoSimuladoHabilitado", true);
        ReflectionTestUtils.setField(service, "publicKey", "pub_prod_abc");
        assertFalse(service.pagoSimuladoDisponible());
        assertThrows(ResponseStatusException.class, () -> service.simularPagoAprobado("DRV-REF1", "cliente@demo.com"));
        verifyNoInteractions(pagoMarketplace);
    }

    @Test
    void pagoSimuladoSoloElDueno() {
        ReflectionTestUtils.setField(service, "pagoSimuladoHabilitado", true);
        when(usuarioRepository.findByCorreo("otro@demo.com"))
                .thenReturn(Optional.of(Usuario.builder().id("u9").correo("otro@demo.com").build()));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.simularPagoAprobado("DRV-REF1", "otro@demo.com"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(pagoMarketplace);
    }

    @Test
    void pagoSimuladoUsaLaMismaConfirmacion() {
        ReflectionTestUtils.setField(service, "pagoSimuladoHabilitado", true);
        Map<String, Object> r = service.simularPagoAprobado("DRV-REF1", "cliente@demo.com");
        // Mismo camino que un pago real: monto verificado y confirmación idempotente del marketplace
        verify(pagoMarketplace).confirmar(pago, "SIMULADO-DRV-REF1");
        assertEquals("APPROVED", r.get("estado"));
        assertEquals("o1", r.get("ordenId"));
    }
}
