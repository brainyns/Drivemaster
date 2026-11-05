package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.drivemaster.drivemaster.model.PedidoTienda;

/** Semáforo del monitor del SUPERADMIN: despacho con el plazo de siempre y "sin retirar" con el criterio de la tienda. */
class SemaforoPedidoTest {

    private static final LocalDateTime PAGO = LocalDateTime.of(2026, 10, 26, 8, 0);
    // Plazo de 48 horas para que el 25 % sean 12 horas exactas
    private static final LocalDateTime PLAZO = PAGO.plusHours(48);

    private static PedidoTienda porDespachar(String estado) {
        return PedidoTienda.builder().id("p1").estado(estado).fechaCreacion(PAGO).plazoDespachoHasta(PLAZO).build();
    }

    // ─── Despacho ───────────────────────────────────────────────────────────

    @Test
    void despachoVerdeMientrasQuedaAlMenosEl25PorCientoDelPlazo() {
        assertEquals(SemaforoPedido.VERDE, SemaforoPedido.despacho(porDespachar(PedidoTienda.PENDIENTE), PAGO.plusHours(1)));
        // Quedan 12 de 48 horas: justo el 25 %, todavía verde
        assertEquals(SemaforoPedido.VERDE, SemaforoPedido.despacho(porDespachar(PedidoTienda.PENDIENTE), PLAZO.minusHours(12)));
    }

    @Test
    void despachoAmarilloCuandoQuedaMenosDel25PorCiento() {
        assertEquals(SemaforoPedido.AMARILLO,
                SemaforoPedido.despacho(porDespachar(PedidoTienda.EN_PREPARACION), PLAZO.minusHours(12).plusMinutes(1)));
        // En el límite exacto del plazo aún no está vencido
        assertEquals(SemaforoPedido.AMARILLO, SemaforoPedido.despacho(porDespachar(PedidoTienda.PENDIENTE), PLAZO));
    }

    @Test
    void despachoRojoCuandoElPlazoVencio() {
        assertEquals(SemaforoPedido.ROJO, SemaforoPedido.despacho(porDespachar(PedidoTienda.PENDIENTE), PLAZO.plusMinutes(1)));
    }

    @Test
    void sinSemaforoDeDespachoSiYaSalioDeLaTiendaONoTienePlazo() {
        for (String estado : new String[]{PedidoTienda.DESPACHADO, PedidoTienda.LISTO_PARA_RETIRAR, PedidoTienda.ENTREGADO,
                PedidoTienda.NO_DESPACHADO}) {
            assertNull(SemaforoPedido.despacho(porDespachar(estado), PLAZO.plusDays(3)), estado);
        }
        PedidoTienda sinPlazo = porDespachar(PedidoTienda.PENDIENTE).toBuilder().plazoDespachoHasta(null).build();
        assertNull(SemaforoPedido.despacho(sinPlazo, PLAZO.plusDays(3)));
        assertNull(SemaforoPedido.despacho(null, PLAZO));
    }

    @Test
    void sinFechaDePagoNoHayProporcion_quedaVerdeHastaVencer() {
        PedidoTienda sinPago = porDespachar(PedidoTienda.PENDIENTE).toBuilder().fechaCreacion(null).build();
        assertEquals(SemaforoPedido.VERDE, SemaforoPedido.despacho(sinPago, PLAZO.minusMinutes(1)));
        assertEquals(SemaforoPedido.ROJO, SemaforoPedido.despacho(sinPago, PLAZO.plusMinutes(1)));
    }

    // ─── Retiro ─────────────────────────────────────────────────────────────

    private static PedidoTienda listoHace(long dias, LocalDateTime ahora) {
        return PedidoTienda.builder().id("r1").estado(PedidoTienda.LISTO_PARA_RETIRAR).entrega(PedidoTienda.RETIRO)
                .fechaListoRetiro(ahora.minusDays(dias)).build();
    }

    @Test
    void sinRetirarConElMismoCriterioDeLaTienda_3DiasAmarillo_15Rojo() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 27, 10, 0);
        assertEquals(SemaforoPedido.VERDE, SemaforoPedido.retiro(listoHace(2, ahora), ahora));
        assertEquals(SemaforoPedido.AMARILLO, SemaforoPedido.retiro(listoHace(RetiroPendienteService.DIAS_PRIMER_RECORDATORIO, ahora), ahora));
        assertEquals(SemaforoPedido.AMARILLO, SemaforoPedido.retiro(listoHace(14, ahora), ahora));
        assertEquals(SemaforoPedido.ROJO, SemaforoPedido.retiro(listoHace(RetiroPendienteService.DIAS_REVISION, ahora), ahora));
    }

    @Test
    void sinSemaforoDeRetiroSiNoEstaEsperandoQueLoRecojan() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 27, 10, 0);
        PedidoTienda retirado = listoHace(20, ahora).toBuilder().estado(PedidoTienda.RETIRADO).build();
        assertNull(SemaforoPedido.retiro(retirado, ahora));
        assertNull(SemaforoPedido.retiro(listoHace(5, ahora).toBuilder().fechaListoRetiro(null).build(), ahora));
    }

    // ─── Las mismas reglas como consultas ───────────────────────────────────

    @Test
    void lasConsultasUsanLosMismosCamposYLaProporcionDel25PorCiento() {
        LocalDateTime ahora = PAGO.plusHours(40);
        String vencido = SemaforoPedido.despachoVencido(ahora).getCriteriaObject().toString();
        assertTrue(vencido.contains("plazoDespachoHasta") && vencido.contains("$lt") && vencido.contains("PENDIENTE"), vencido);

        String porVencer = SemaforoPedido.despachoPorVencer(ahora).getCriteriaObject().toString();
        assertTrue(porVencer.contains("$expr") && porVencer.contains("$subtract") && porVencer.contains("$fechaCreacion"), porVencer);
        assertTrue(porVencer.contains("25") && porVencer.contains("$gte"), porVencer);

        String sinRetirar = SemaforoPedido.sinRetirar(ahora).getCriteriaObject().toString();
        assertTrue(sinRetirar.contains("LISTO_PARA_RETIRAR") && sinRetirar.contains("fechaListoRetiro"), sinRetirar);
    }
}
