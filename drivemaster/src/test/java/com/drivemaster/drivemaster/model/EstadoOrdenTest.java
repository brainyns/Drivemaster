package com.drivemaster.drivemaster.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.drivemaster.drivemaster.model.EstadoOrden.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoOrdenTest {

    private static final List<String> ESTADOS = List.of(ENVIADA, ASIGNADA_A_TALLER, REASIGNADA, EN_REVISION, DIAGNOSTICADA, COTIZADA, APROBADA,
            PAGADA, REPUESTOS_PEDIDOS, REPUESTOS_RECIBIDOS, EN_INSTALACION, LISTA, ENTREGADA, RECHAZADA, CANCELADA);
    private static final List<String> ACTORES = List.of(SISTEMA, CLIENTE, TALLER, MECANICO, ADMIN);

    private static String t(String desde, String hacia, String... actores) {
        return desde + ">" + hacia + ":" + String.join(",", actores);
    }

    /** La tabla completa. Una transición nueva, o una quitada, debe cambiarse aquí a propósito. */
    private static final List<String> PERMITIDAS = List.of(
            t(ENVIADA, ASIGNADA_A_TALLER, SISTEMA), t(ENVIADA, RECHAZADA, SISTEMA), t(ENVIADA, CANCELADA, CLIENTE, ADMIN),
            t(ASIGNADA_A_TALLER, REASIGNADA, SISTEMA, TALLER, ADMIN), t(ASIGNADA_A_TALLER, EN_REVISION, TALLER, SISTEMA),
            t(ASIGNADA_A_TALLER, CANCELADA, CLIENTE, ADMIN, SISTEMA),
            t(REASIGNADA, ASIGNADA_A_TALLER, SISTEMA), t(REASIGNADA, RECHAZADA, SISTEMA), t(REASIGNADA, CANCELADA, CLIENTE, ADMIN),
            t(EN_REVISION, DIAGNOSTICADA, TALLER, MECANICO), t(EN_REVISION, ASIGNADA_A_TALLER, SISTEMA), t(EN_REVISION, CANCELADA, ADMIN),
            t(DIAGNOSTICADA, COTIZADA, TALLER, MECANICO), t(DIAGNOSTICADA, CANCELADA, CLIENTE, ADMIN),
            t(COTIZADA, APROBADA, CLIENTE), t(COTIZADA, DIAGNOSTICADA, CLIENTE, SISTEMA), t(COTIZADA, CANCELADA, CLIENTE, ADMIN),
            t(APROBADA, PAGADA, SISTEMA), t(APROBADA, COTIZADA, SISTEMA), t(APROBADA, CANCELADA, CLIENTE, ADMIN),
            t(PAGADA, REPUESTOS_PEDIDOS, SISTEMA), t(PAGADA, REPUESTOS_RECIBIDOS, SISTEMA), t(PAGADA, DIAGNOSTICADA, SISTEMA), t(PAGADA, CANCELADA, ADMIN),
            t(REPUESTOS_PEDIDOS, REPUESTOS_RECIBIDOS, TALLER), t(REPUESTOS_PEDIDOS, CANCELADA, ADMIN),
            t(REPUESTOS_RECIBIDOS, EN_INSTALACION, TALLER, MECANICO),
            t(EN_INSTALACION, LISTA, TALLER, MECANICO),
            t(LISTA, ENTREGADA, TALLER));

    @Test
    void cadaCombinacionDeEstadoDestinoYActorEsLaDeLaTabla() {
        Set<String> esperadas = new HashSet<>();
        for (String fila : PERMITIDAS) {
            String[] partes = fila.split(":");
            for (String actor : partes[1].split(",")) esperadas.add(partes[0] + ":" + actor);
        }
        int revisadas = 0;
        for (String desde : ESTADOS) {
            for (String hacia : ESTADOS) {
                for (String actor : ACTORES) {
                    assertEquals(esperadas.contains(desde + ">" + hacia + ":" + actor), permitida(desde, hacia, actor),
                            desde + " a " + hacia + " por " + actor);
                    revisadas++;
                }
            }
        }
        assertEquals(15 * 15 * 5, revisadas);
    }

    @Test
    void elTrabajoEnElTallerNoSeSaltaPasos() {
        for (String desde : List.of(PAGADA, REPUESTOS_PEDIDOS, APROBADA, DIAGNOSTICADA)) {
            assertFalse(permitida(desde, EN_INSTALACION, TALLER), desde);
            assertFalse(permitida(desde, LISTA, TALLER), desde);
            assertFalse(permitida(desde, ENTREGADA, TALLER), desde);
        }
        assertFalse(permitida(EN_INSTALACION, ENTREGADA, TALLER), "primero queda lista");
    }

    @Test
    void elTallerNoSeMarcaPagosNiElClienteMueveElTrabajo() {
        assertFalse(permitida(APROBADA, PAGADA, TALLER));
        assertFalse(permitida(APROBADA, PAGADA, CLIENTE));
        assertFalse(permitida(LISTA, ENTREGADA, CLIENTE));
        assertFalse(permitida(LISTA, ENTREGADA, MECANICO), "el mecánico no tiene cuenta y no entrega");
    }

    @Test
    void soloElSistemaAsignaTalleres() {
        assertTrue(permitida(EN_REVISION, ASIGNADA_A_TALLER, SISTEMA), "pago del diagnóstico que no se pudo aplicar");
        assertFalse(permitida(EN_REVISION, ASIGNADA_A_TALLER, TALLER), "el taller no puede deshacer un pago");
        assertFalse(permitida(EN_REVISION, ASIGNADA_A_TALLER, CLIENTE));
        assertTrue(permitida(ENVIADA, ASIGNADA_A_TALLER, SISTEMA));
        assertFalse(permitida(ENVIADA, ASIGNADA_A_TALLER, CLIENTE));
        assertFalse(permitida(ENVIADA, ASIGNADA_A_TALLER, TALLER));
    }

    @Test
    void elTallerPuedeRechazarYAceptarPeroNoCancelar() {
        assertTrue(permitida(ASIGNADA_A_TALLER, REASIGNADA, TALLER));
        assertTrue(permitida(ASIGNADA_A_TALLER, EN_REVISION, TALLER));
        assertFalse(permitida(ASIGNADA_A_TALLER, CANCELADA, TALLER));
    }

    @Test
    void elClienteCancelaAntesDeLaRevisionPeroNoDespues() {
        assertTrue(permitida(ENVIADA, CANCELADA, CLIENTE));
        assertTrue(permitida(ASIGNADA_A_TALLER, CANCELADA, CLIENTE));
        assertFalse(permitida(EN_REVISION, CANCELADA, CLIENTE));
        assertTrue(permitida(EN_REVISION, CANCELADA, ADMIN));
    }

    @Test
    void elRechazoDeLaCotizacionCancelaLaOrden() {
        assertTrue(permitida(COTIZADA, CANCELADA, CLIENTE));
        assertTrue(permitida(COTIZADA, APROBADA, CLIENTE));
        assertFalse(permitida(COTIZADA, APROBADA, TALLER));
    }

    @Test
    void estadosFinalesNoTienenSalida() {
        assertFalse(permitida(ENTREGADA, CANCELADA, ADMIN));
        assertFalse(permitida(RECHAZADA, ASIGNADA_A_TALLER, SISTEMA));
        assertFalse(permitida(CANCELADA, ENVIADA, CLIENTE));
        assertFalse(ACTIVOS.contains(ENTREGADA));
        assertFalse(ACTIVOS.contains(CANCELADA));
        assertFalse(ACTIVOS.contains(RECHAZADA));
    }

    @Test
    void cadaEstadoTieneSuNombreParaLaPantalla() throws Exception {
        for (java.lang.reflect.Field f : EstadoOrden.class.getFields()) {
            if (java.util.Set.of("ENVIADA", "ASIGNADA_A_TALLER", "REASIGNADA", "EN_REVISION", "DIAGNOSTICADA", "COTIZADA", "APROBADA", "PAGADA",
                    "REPUESTOS_PEDIDOS", "REPUESTOS_RECIBIDOS", "EN_INSTALACION", "LISTA", "ENTREGADA", "RECHAZADA", "CANCELADA").contains(f.getName())) {
                String estado = (String) f.get(null);
                assertFalse(estado.equals(nombre(estado)), estado + " no tiene nombre para la pantalla");
            }
        }
        assertEquals("Otro", nombre("Otro"));
    }

    @Test
    void superadminActuaComoAdmin() {
        assertEquals(ADMIN, actorDeRol("SUPERADMIN"));
        assertEquals(ADMIN, actorDeRol("ADMIN"));
        assertEquals(TALLER, actorDeRol("TALLER"));
    }
}
