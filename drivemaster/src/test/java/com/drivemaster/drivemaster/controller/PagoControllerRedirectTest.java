package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.service.PagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Wompi devuelve al cliente a confirmar-redirect: siempre debe terminar en el frontend, nunca en un 500 de Render. */
class PagoControllerRedirectTest {

    private static final String FRONTEND = "https://drivemaster-orcin.vercel.app";

    private PagoService pagoService;
    private PagoController controller;

    @BeforeEach
    void setUp() {
        pagoService = mock(PagoService.class);
        controller = new PagoController(pagoService);
        ReflectionTestUtils.setField(controller, "frontendUrl", FRONTEND);
    }

    private static String destino(ResponseEntity<Void> r) {
        assertEquals(302, r.getStatusCode().value());
        return r.getHeaders().getLocation().toString();
    }

    @Test
    void conVerificacionCorrectaVuelveAlResultadoSinMarcaDeError() {
        when(pagoService.verificarTransaccion("123-abc")).thenReturn(Map.of("estado", "APPROVED"));

        assertEquals(FRONTEND + "/?pago=resultado&id=123-abc", destino(controller.confirmarRedirect("123-abc")));
        verify(pagoService).verificarTransaccion("123-abc");
    }

    @Test
    void siLaVerificacionFallaRedirigeIgualConEstadoDeError() {
        when(pagoService.verificarTransaccion("123-abc")).thenThrow(new RuntimeException("Wompi no responde"));

        assertEquals(FRONTEND + "/?pago=resultado&id=123-abc&verificacion=error",
                destino(controller.confirmarRedirect("123-abc")));
    }

    @Test
    void sinIdNoVerificaYVuelveAlResultado() {
        assertEquals(FRONTEND + "/?pago=resultado", destino(controller.confirmarRedirect(null)));
        assertEquals(FRONTEND + "/?pago=resultado", destino(controller.confirmarRedirect("  ")));
        verifyNoInteractions(pagoService);
    }

    @Test
    void quitaLaBarraFinalDeFrontendUrlYCodificaElId() {
        ReflectionTestUtils.setField(controller, "frontendUrl", FRONTEND + "/");

        // Un id manipulado no rompe la URL ni agrega parámetros
        assertEquals(FRONTEND + "/?pago=resultado&id=a%26pago%3Dotro",
                destino(controller.confirmarRedirect("a&pago=otro")));
    }
}
