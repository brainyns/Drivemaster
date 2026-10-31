package com.drivemaster.drivemaster.service.dispersion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La firma se calcula como en la documentación de Wompi: propiedades del evento + timestamp + secreto, en SHA-256. */
class FirmaEventosPayoutTest {

    private static final String SECRETO = "secreto_eventos_prueba";
    private static final List<String> PROPIEDADES = List.of("transaction.id", "transaction.status", "transaction.amountInCents");

    private final FirmaEventosPayout firma = new FirmaEventosPayout(SECRETO, "WOMPI");

    private static Map<String, Object> evento(String status, String checksum) {
        return Map.of(
                "event", "transaction.updated",
                "data", Map.of("transaction", Map.of("id", "txn-1", "payoutId", "pout-1", "status", status, "amountInCents", 7140000)),
                "signature", Map.of("properties", PROPIEDADES, "checksum", checksum),
                "timestamp", 1730000000123L);
    }

    private static String correcto() {
        return FirmaEventosPayout.sha256("txn-1" + "FAILED" + "7140000" + "1730000000123" + SECRETO);
    }

    @Test
    void aceptaLaFirmaDelCuerpoYLaDeLaCabecera() {
        assertTrue(firma.esValida(evento("FAILED", correcto()), null));
        assertTrue(firma.esValida(evento("FAILED", "ignorada"), correcto()));
        assertTrue(firma.esValida(evento("FAILED", correcto().toUpperCase()), null), "el hexadecimal no distingue mayúsculas");
    }

    @Test
    void rechazaUnEventoAlteradoOFirmadoConOtroSecreto() {
        assertFalse(firma.esValida(evento("APPROVED", correcto()), null), "el estado cambió después de firmar");
        assertFalse(firma.esValida(evento("FAILED", "abc"), null));
        assertFalse(new FirmaEventosPayout("otro_secreto", "WOMPI").esValida(evento("FAILED", correcto()), null));
    }

    @Test
    void sinSecretoSeRechazaTodoInclusoUnaFirmaHechaConSecretoVacio() {
        String conSecretoVacio = FirmaEventosPayout.sha256("txn-1" + "FAILED" + "7140000" + "1730000000123");
        assertFalse(new FirmaEventosPayout("", "WOMPI").esValida(evento("FAILED", conSecretoVacio), null));
        assertFalse(new FirmaEventosPayout("   ", "WOMPI").esValida(evento("FAILED", conSecretoVacio), null));
        assertFalse(new FirmaEventosPayout(null, "MANUAL").esValida(evento("FAILED", conSecretoVacio), null));
    }

    @Test
    void rechazaEventosIncompletos() {
        assertFalse(firma.esValida(null, null));
        assertFalse(firma.esValida(Map.of("event", "payout.updated"), null));
        assertFalse(firma.esValida(Map.of(
                "data", Map.of("transaction", Map.of("id", "txn-1")),
                "signature", Map.of("properties", List.of(), "checksum", correcto()),
                "timestamp", 1L), null), "sin propiedades no hay nada firmado");
        assertFalse(firma.esValida(Map.of(
                "data", Map.of("transaction", Map.of("id", "txn-1")),
                "signature", Map.of("properties", List.of("transaction.noExiste"), "checksum", correcto()),
                "timestamp", 1L), null));
    }
}
