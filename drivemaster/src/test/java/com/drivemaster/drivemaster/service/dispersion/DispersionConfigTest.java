package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DispersionConfigTest {

    @Test
    void simuladoArrancaConLlavesDePrueba() {
        DispersionService d = DispersionConfig.crear("SIMULADO", "pub_test_abc");
        assertEquals("SIMULADO", d.modo());
        assertTrue(d.automatica());
    }

    @Test
    void simuladoNoArrancaConLlavesReales() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DispersionConfig.crear("SIMULADO", "pub_prod_abc"));
        assertTrue(e.getMessage().contains("MANUAL"));
    }

    @Test
    void simuladoNoArrancaSinLlaves() {
        assertThrows(IllegalStateException.class, () -> DispersionConfig.crear("SIMULADO", ""));
        assertThrows(IllegalStateException.class, () -> DispersionConfig.crear("SIMULADO", null));
    }

    @Test
    void manualArrancaConLlavesReales() {
        DispersionService d = DispersionConfig.crear("manual", "pub_prod_abc");
        assertEquals("MANUAL", d.modo());
        assertFalse(d.automatica());
    }

    @Test
    void unModoDesconocidoNoArranca() {
        assertThrows(IllegalStateException.class, () -> DispersionConfig.crear("WOMPI", "pub_test_abc"));
        assertThrows(IllegalStateException.class, () -> DispersionConfig.crear("", "pub_test_abc"));
        assertThrows(IllegalStateException.class, () -> DispersionConfig.crear(null, "pub_test_abc"));
    }

    private static RepartoPago reparto(double monto) {
        return RepartoPago.builder().id(5L).monto(monto).build();
    }

    @Test
    void simuladaGeneraReferenciaSimEnPesosEnteros() {
        ResultadoDispersion r = new SimulatedDispersionService()
                .liquidar(reparto(71_400.4), SolicitudDispersion.automatica());
        assertEquals("SIMULADO", r.modo());
        assertTrue(r.referencia().startsWith("SIM-5-"), r.referencia());
        assertEquals(new BigDecimal("71400"), r.monto());
    }

    @Test
    void simuladaRechazaMontosNoPositivos() {
        assertThrows(DispersionException.class,
                () -> new SimulatedDispersionService().liquidar(reparto(0), SolicitudDispersion.automatica()));
    }

    @Test
    void manualExigeElNumeroDeTransferencia() {
        ManualDispersionService m = new ManualDispersionService();
        for (String numero : new String[]{null, "", "   ", "ab", "con espacios y @"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> m.validar(new SolicitudDispersion("admin@demo.com", numero, null)), "número: " + numero);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    void manualUsaElNumeroComoReferencia() {
        ResultadoDispersion r = new ManualDispersionService()
                .liquidar(reparto(95_200), new SolicitudDispersion("admin@demo.com", " TRF-20261007-01 ", null));
        assertEquals("MANUAL", r.modo());
        assertEquals("TRF-20261007-01", r.referencia());
        assertEquals(new BigDecimal("95200"), r.monto());
    }
}
