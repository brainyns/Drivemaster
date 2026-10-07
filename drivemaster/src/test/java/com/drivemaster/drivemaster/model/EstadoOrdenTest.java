package com.drivemaster.drivemaster.model;

import org.junit.jupiter.api.Test;

import static com.drivemaster.drivemaster.model.EstadoOrden.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoOrdenTest {

    @Test
    void soloElSistemaAsignaTalleres() {
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
    void superadminActuaComoAdmin() {
        assertEquals(ADMIN, actorDeRol("SUPERADMIN"));
        assertEquals(ADMIN, actorDeRol("ADMIN"));
        assertEquals(TALLER, actorDeRol("TALLER"));
    }
}
