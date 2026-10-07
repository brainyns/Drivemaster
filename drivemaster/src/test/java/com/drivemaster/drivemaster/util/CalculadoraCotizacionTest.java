package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.CotizacionItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraCotizacionTest {

    private static CotizacionItem repuesto(int cantidad, double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).cantidad(cantidad).precioUnitario(precio).build();
    }

    private static CotizacionItem manoObraFija(double valor) {
        return CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).modalidad(CotizacionItem.POR_ITEM)
                .cantidad(1).precioUnitario(valor).build();
    }

    private static CotizacionItem manoObraHoras(double horas, double tarifa) {
        return CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).modalidad(CotizacionItem.POR_HORAS)
                .horas(horas).precioUnitario(tarifa).build();
    }

    @Test
    void calculaSubtotalesIvaYTotal() {
        List<CotizacionItem> items = List.of(repuesto(2, 45_000), manoObraFija(80_000));
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(items, 19, 0, 10);

        assertEquals(90_000, t.subtotalRepuestos());
        assertEquals(80_000, t.subtotalManoObra());
        assertEquals(170_000, t.subtotal());
        assertEquals(17_100, t.ivaRepuestos());
        assertEquals(15_200, t.ivaManoObra());
        assertEquals(32_300, t.iva());
        assertEquals(202_300, t.total());
        assertEquals(202_300, t.totalAPagar());
        assertEquals(90_000, items.get(0).getSubtotal());
    }

    @Test
    void ivaSaleDelParametroRecibido() {
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(List.of(manoObraFija(100_000)), 5, 0, 0);
        assertEquals(5_000, t.iva());
        assertEquals(105_000, t.total());
    }

    @Test
    void manoObraPorHorasMultiplicaHorasPorTarifa() {
        List<CotizacionItem> items = List.of(manoObraHoras(1.5, 60_000));
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(items, 19, 0, 0);
        assertEquals(90_000, t.subtotalManoObra());
        assertEquals(90_000, items.get(0).getSubtotal());
    }

    @Test
    void anticipoSeRestaDelTotalConIva() {
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(
                List.of(repuesto(1, 100_000), manoObraFija(100_000)), 19, 50_000, 0);
        assertEquals(238_000, t.total());
        assertEquals(50_000, t.anticipo());
        assertEquals(188_000, t.totalAPagar());
    }

    @Test
    void anticipoTieneTopeEnLaManoDeObraConIva() {
        // Mano de obra 30.000 + IVA 5.700 = 35.700: el anticipo de 50.000 se recorta
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(
                List.of(repuesto(1, 100_000), manoObraFija(30_000)), 19, 50_000, 0);
        assertEquals(35_700, t.anticipo());
        assertEquals(t.total() - 35_700, t.totalAPagar());
    }

    @Test
    void sinManoDeObraNoHayAnticipo() {
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(List.of(repuesto(1, 100_000)), 19, 50_000, 0);
        assertEquals(0, t.anticipo());
        assertEquals(119_000, t.totalAPagar());
    }

    @Test
    void comisionInformativaSobreElSubtotalSinIva() {
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(
                List.of(repuesto(1, 100_000), manoObraFija(50_000)), 19, 0, 10);
        assertEquals(15_000, t.comision());
        // La comisión no cambia lo que paga el cliente
        assertEquals(t.total(), t.totalAPagar());
    }

    @Test
    void redondeaAlPeso() {
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(List.of(manoObraFija(10_001)), 19, 0, 0);
        assertEquals(1_900, t.iva()); // 1.900,19 → 1.900
    }
}
