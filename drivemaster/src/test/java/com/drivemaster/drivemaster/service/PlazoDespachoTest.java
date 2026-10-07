package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.util.PlazoHabil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plazo máximo de despacho: stock = 2 días hábiles; encargo = días estimados + 2;
 * un pedido mixto usa el ítem más tardío. Siempre desde el pago.
 */
class PlazoDespachoTest {

    // Miércoles 7-oct-2026, 12:00 de Bogotá. Hábiles siguientes: jue 8, vie 9, (lun 12 festivo), mar 13, mié 14...
    private static final LocalDateTime PAGO = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, PlazoHabil.ZONA_COLOMBIA)
            .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

    private static CotizacionItem item(String disponibilidad, Integer dias) {
        return CotizacionItem.builder().productoId("p").cantidad(1).disponibilidad(disponibilidad).diasEntrega(dias).build();
    }

    private static LocalDateTime habiles(int dias) {
        return PlazoHabil.sumarDiasHabiles(PAGO, dias);
    }

    @Test
    void stockSon2DiasHabiles() {
        assertEquals(habiles(2), PagoMarketplaceService.plazoDespacho(List.of(item("DISPONIBLE", null)), PAGO, 2, 2));
    }

    @Test
    void encargoSonLosDiasEstimadosMasDos() {
        assertEquals(habiles(5), PagoMarketplaceService.plazoDespacho(List.of(item("ENCARGO", 3)), PAGO, 2, 2));
    }

    @Test
    void encargoSinEstimadoSoloSumaLosDiasExtra() {
        assertEquals(habiles(2), PagoMarketplaceService.plazoDespacho(List.of(item("ENCARGO", null)), PAGO, 2, 2));
    }

    @Test
    void pedidoMixtoUsaElItemMasTardio() {
        List<CotizacionItem> mixto = List.of(item("DISPONIBLE", null), item("ENCARGO", 3), item("ENCARGO", 6));
        assertEquals(habiles(8), PagoMarketplaceService.plazoDespacho(mixto, PAGO, 2, 2));
    }

    @Test
    void losParametrosManda() {
        assertEquals(habiles(4), PagoMarketplaceService.plazoDespacho(List.of(item("DISPONIBLE", null)), PAGO, 4, 2));
        assertEquals(habiles(7), PagoMarketplaceService.plazoDespacho(List.of(item("ENCARGO", 3)), PAGO, 2, 4));
    }

    @Test
    void elStockSaltaFestivosYFinesDeSemana() {
        // mié 7 + 2 hábiles = vie 9
        assertEquals(java.time.LocalDate.of(2026, 10, 9),
                PagoMarketplaceService.plazoDespacho(List.of(item("DISPONIBLE", null)), PAGO, 2, 2)
                        .atZone(ZoneId.systemDefault()).withZoneSameInstant(PlazoHabil.ZONA_COLOMBIA).toLocalDate());
        // mié 7 + 3 = martes 13 (el lunes 12 es festivo)
        assertEquals(java.time.LocalDate.of(2026, 10, 13),
                PagoMarketplaceService.plazoDespacho(List.of(item("DISPONIBLE", null)), PAGO, 3, 2)
                        .atZone(ZoneId.systemDefault()).withZoneSameInstant(PlazoHabil.ZONA_COLOMBIA).toLocalDate());
    }
}
