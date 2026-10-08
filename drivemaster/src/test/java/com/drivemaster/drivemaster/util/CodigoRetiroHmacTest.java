package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.config.CodigoRetiroConfig;
import com.drivemaster.drivemaster.testutil.LogsCapturados;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El código de retiro se guarda con HMAC-SHA256 y una clave del servidor; los emitidos antes siguen validando. */
class CodigoRetiroHmacTest {

    private static final String CLAVE = "clave-de-prueba-larga-y-aleatoria-0123456789";

    @BeforeEach
    void sinClave() {
        CodigoRetiro.configurar(null);
    }

    @AfterEach
    void restaurar() {
        CodigoRetiro.configurar(null); // el estado es estático: no se filtra a otras pruebas
    }

    private static String sha256Antiguo(String pedidoId, String codigoNormalizado) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((pedidoId + ":" + codigoNormalizado).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void conClaveSeGuardaUnHmacConPrefijoYSinElCodigo() {
        CodigoRetiro.configurar(CLAVE);

        String guardado = CodigoRetiro.hash("p1", "ABCD-1234");

        assertTrue(guardado.startsWith("hmac1:"));
        assertEquals("hmac1:".length() + 64, guardado.length());
        assertFalse(guardado.contains("ABCD1234"));
        assertFalse(CodigoRetiro.esLegado(guardado));
        assertTrue(CodigoRetiro.coincide(guardado, "p1", "abcd 1234"));
        assertFalse(CodigoRetiro.coincide(guardado, "p2", "ABCD-1234"), "ligado al pedido");
        assertFalse(CodigoRetiro.coincide(guardado, "p1", "0000-0000"));
    }

    @Test
    void elHmacNoSePuedeCalcularSinLaClaveDelServidor() throws Exception {
        CodigoRetiro.configurar(CLAVE);
        String guardado = CodigoRetiro.hash("p1", "ABCD-1234");

        // Quien tenga solo la base (sin la clave) no obtiene el mismo valor con el SHA-256 de siempre
        assertNotEquals(sha256Antiguo("p1", "ABCD1234"), guardado.substring("hmac1:".length()));

        // Otra clave produce otro valor y no valida
        CodigoRetiro.configurar("otra-clave-distinta-de-32-caracteres-o-mas-xyz");
        assertNotEquals(guardado, CodigoRetiro.hash("p1", "ABCD-1234"));
        assertFalse(CodigoRetiro.coincide(guardado, "p1", "ABCD-1234"));
    }

    @Test
    void losCodigosEmitidosAntesDelHmacSiguenValidando() throws Exception {
        String antiguo = sha256Antiguo("p1", "ABCD1234");
        assertTrue(CodigoRetiro.esLegado(antiguo));

        CodigoRetiro.configurar(CLAVE); // ya con la clave nueva
        assertTrue(CodigoRetiro.coincide(antiguo, "p1", "ABCD-1234"));
        assertFalse(CodigoRetiro.coincide(antiguo, "p1", "ABCD-1235"));
        assertFalse(CodigoRetiro.coincide(antiguo, "p2", "ABCD-1234"));

        CodigoRetiro.configurar(null); // y también si la clave faltara
        assertTrue(CodigoRetiro.coincide(antiguo, "p1", "ABCD-1234"));
    }

    @Test
    void sinClaveUnHmacYaEmitidoNoValidaYLosNuevosUsanElEsquemaAnterior() {
        CodigoRetiro.configurar(CLAVE);
        String hmac = CodigoRetiro.hash("p1", "ABCD-1234");

        CodigoRetiro.configurar(null);
        assertFalse(CodigoRetiro.coincide(hmac, "p1", "ABCD-1234"), "sin clave no se puede verificar");
        assertTrue(CodigoRetiro.esLegado(CodigoRetiro.hash("p1", "ABCD-1234")), "sin clave se cae al SHA-256 anterior");
    }

    // ─── Aviso al arrancar ───────────────────────────────────────────────────

    private List<String> arrancarConClave(String clave) {
        CodigoRetiroConfig config = new CodigoRetiroConfig(clave);
        return LogsCapturados.de(() -> ReflectionTestUtils.invokeMethod(config, "configurar"));
    }

    @Test
    void siFaltaLaClaveElBackendAvisaAlArrancar() {
        for (String falta : new String[]{"", "   ", null}) {
            List<String> logs = arrancarConClave(falta);
            assertTrue(logs.stream().anyMatch(m -> m.contains("Falta RETIRO_CODIGO_SECRET")), "clave: " + falta);
            assertFalse(CodigoRetiro.configurado());
        }
    }

    @Test
    void unaClaveCortaAvisaYUnaLargaNoAvisaNadaNiSeEscribe() {
        List<String> corta = arrancarConClave("corta");
        assertTrue(corta.stream().anyMatch(m -> m.contains("es corta")));
        assertFalse(corta.stream().anyMatch(m -> m.contains("corta") && !m.contains("es corta") && m.contains("=")),
                "la clave no se escribe");

        List<String> larga = arrancarConClave(CLAVE);
        assertTrue(larga.stream().noneMatch(m -> m.contains("RETIRO_CODIGO_SECRET")), "una clave larga no genera avisos: " + larga);
        assertTrue(larga.stream().noneMatch(m -> m.contains(CLAVE)), "la clave nunca se escribe en el log");
        assertTrue(CodigoRetiro.configurado());
    }
}
