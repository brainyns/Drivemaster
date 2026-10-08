package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.SolicitudRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Flujo viejo (compra normal): un fallo de Brevo nunca rompe un pago ni un cambio de estado ya hecho.
 */
class CorreoFlujoViejoTest {

    private EmailsService correo;
    private UsuarioRepository usuarios;
    private SolicitudRepository solicitudes;

    @BeforeEach
    void setUp() {
        correo = mock(EmailsService.class);
        doThrow(new RuntimeException("Error al enviar el correo: Brevo respondió 400"))
                .when(correo).enviarEmail(anyString(), anyString(), anyString());
        usuarios = mock(UsuarioRepository.class);
        when(usuarios.findById("u1")).thenReturn(Optional.of(Usuario.builder().id("u1").nombre("Ana").correo("ana@demo.com").build()));
        solicitudes = mock(SolicitudRepository.class);
        when(solicitudes.save(any(Solicitud.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Solicitud solicitud(String estado) {
        Solicitud s = new Solicitud();
        s.setId("s1");
        s.setClienteId("u1");
        s.setEstado(estado);
        s.setTotal(119_000.0);
        return s;
    }

    @Test
    void elPagoQuedaAplicadoAunqueFalleElCorreoDeConfirmacion() {
        PagoRepository pagos = mock(PagoRepository.class);
        VentaService ventas = mock(VentaService.class);
        Venta venta = new Venta();
        venta.setId("v1");
        when(ventas.registrarVenta(any(Venta.class))).thenReturn(venta);
        Solicitud s = solicitud("PENDIENTE_PAGO");
        when(solicitudes.findById("s1")).thenReturn(Optional.of(s));

        PagoEntity pago = new PagoEntity();
        pago.setId(1L);
        pago.setReferencia("DRV-OLD");
        pago.setSolicitudId("s1");
        pago.setMonto(119_000.0);
        pago.setEstado("PENDIENTE");
        when(pagos.findByReferencia("DRV-OLD")).thenReturn(Optional.of(pago));

        PagoService servicio = new PagoService(solicitudes, pagos, ventas, usuarios, correo, mock(RestTemplate.class),
                mock(PagoMarketplaceService.class));

        Object resultado = ReflectionTestUtils.invokeMethod(servicio, "confirmarPagoAprobado",
                Map.<String, Object>of("reference", "DRV-OLD", "id", "txn-1", "amount_in_cents", 11_900_000, "currency", "COP"));

        assertEquals("s1", resultado, "el pago se confirmó");
        assertEquals("PAGADO", s.getEstado());
        assertEquals("PAGADO", pago.getEstado());
        assertEquals("txn-1", pago.getTransactionId());
        verify(pagos).save(pago);
        verify(correo).enviarEmail(eq("ana@demo.com"), anyString(), anyString()); // se intentó y falló
    }

    private static <T> T eq(T valor) {
        return org.mockito.ArgumentMatchers.eq(valor);
    }

    private SolicitudService solicitudService() {
        return new SolicitudService(solicitudes, usuarios, mock(ProductoRepository.class), mock(VentaService.class), correo,
                mock(ParametroRepository.class), mock(PagoService.class), mock(NotificationService.class));
    }

    @Test
    void marcarEntregadoNoSeRompeSiFallaElCorreo() {
        Solicitud s = solicitud("EN_CAMINO");
        when(solicitudes.findById("s1")).thenReturn(Optional.of(s));

        assertDoesNotThrow(() -> solicitudService().marcarEntregado("s1"));

        assertEquals("ENTREGADO", s.getEstado());
        verify(solicitudes).save(s);
    }

    @Test
    void rechazarNoSeRompeSiFallaElCorreo() {
        Solicitud s = solicitud("PENDIENTE");
        when(solicitudes.findById("s1")).thenReturn(Optional.of(s));

        assertDoesNotThrow(() -> solicitudService().rechazar("s1", "Sin stock"));

        assertEquals("RECHAZADO", s.getEstado());
        verify(solicitudes, atLeastOnce()).save(s);
    }
}
