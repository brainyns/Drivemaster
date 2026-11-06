package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base de la comisión con el IVA por taller: siempre el precio con IVA (el que tenga el taller, 0 % si no es
 * responsable). La cotización descuenta el adelanto con su IVA, que ya pagó comisión en el diagnóstico. Como la base ya
 * no deja ver el IVA, cada línea lo guarda aparte (ivaIncluido): aquí se prueba que da lo mismo que antes.
 */
class ComisionBaseIvaTallerTest {

    private static final double IVA = 19;

    private static OrdenServicio diagnostico(double base, double pct) {
        return OrdenServicio.builder().tarifaDiagnosticoBase(base).ivaDiagnosticoPct(pct)
                .tarifaDiagnostico(base + Math.round(base * pct / 100)).build();
    }

    @Test
    void elDiagnosticoCobraSobreLaTarifaConSuIvaResponsableONo() {
        assertEquals(new BigDecimal("59500"), ComisionPlataforma.baseDiagnostico(59_500.0));
        assertEquals(new BigDecimal("50000"), ComisionPlataforma.baseDiagnostico(50_000.0));
    }

    @Test
    void elIvaDelDiagnosticoSaleDeSuBaseCongeladaODelIvaGlobalEnLosAnteriores() {
        assertEquals(new BigDecimal("9500"), ComisionPlataforma.ivaDiagnostico(diagnostico(50_000, 19), 59_500.0, IVA));
        assertEquals(BigDecimal.ZERO, ComisionPlataforma.ivaDiagnostico(diagnostico(50_000, 0), 50_000.0, IVA));
        OrdenServicio anterior = OrdenServicio.builder().tarifaDiagnostico(59_500.0).build();
        assertEquals(new BigDecimal("9500"), ComisionPlataforma.ivaDiagnostico(anterior, 59_500.0, IVA));
    }

    private static Cotizacion cotizacion(double repuestos, double manoObra, double pctMano, double anticipo) {
        double ivaR = Math.round(repuestos * IVA / 100);
        double ivaM = Math.round(manoObra * pctMano / 100);
        double total = repuestos + manoObra + ivaR + ivaM;
        return Cotizacion.builder().ivaRepuestosPct(IVA).ivaManoObraPct(pctMano)
                .subtotalRepuestos(repuestos).subtotalManoObra(manoObra).subtotal(repuestos + manoObra)
                .ivaRepuestos(ivaR).ivaManoObra(ivaM).iva(ivaR + ivaM).total(total)
                .anticipoDiagnostico(anticipo).totalAPagar(total - anticipo).build();
    }

    @Test
    void laCotizacionDeUnTallerNoResponsableCobraSobreLoQuePagaElCliente() {
        Cotizacion c = cotizacion(100_000, 80_000, 0, 0);

        // 100.000 + 19.000 de IVA de los repuestos + 80.000 de mano de obra sin IVA
        assertEquals(new BigDecimal("199000"), ComisionPlataforma.baseCotizacion(c, c.getTotalAPagar()));
    }

    @Test
    void laCotizacionDescuentaElAdelantoConSuIva() {
        // Responsable: 214.200 con IVA − 59.500 del diagnóstico (50.000 + 19 %) pagado antes
        Cotizacion responsable = cotizacion(100_000, 80_000, 19, 59_500);
        assertEquals(new BigDecimal("154700"), ComisionPlataforma.baseCotizacion(responsable, responsable.getTotalAPagar()));
        assertEquals(responsable.getTotalAPagar(), ComisionPlataforma.baseCotizacion(responsable, 0.0).doubleValue(),
                "la base es lo que paga el cliente en este pago");

        // No responsable: 199.000 − 50.000 del diagnóstico sin IVA
        Cotizacion noResponsable = cotizacion(100_000, 80_000, 0, 50_000);
        assertEquals(new BigDecimal("149000"), ComisionPlataforma.baseCotizacion(noResponsable, noResponsable.getTotalAPagar()));
    }

    @Test
    void elIvaDeLaLineaDelTallerEsElDeAntes() {
        // Línea del taller: 80.000 + 15.200 − 59.500 = 35.700. Antes su base era 30.000 (130.000 − 100.000 de la tienda)
        Cotizacion responsable = cotizacion(100_000, 80_000, 19, 59_500);
        assertEquals(new BigDecimal("5700"),
                ComisionPlataforma.ivaCotizacionTaller(responsable, diagnostico(50_000, 19), new BigDecimal("19000"), IVA));

        Cotizacion noResponsable = cotizacion(100_000, 80_000, 0, 50_000);
        assertEquals(BigDecimal.ZERO,
                ComisionPlataforma.ivaCotizacionTaller(noResponsable, diagnostico(50_000, 0), new BigDecimal("19000"), IVA));
    }

    @Test
    void lasCotizacionesSinTotalesGuardadosCobranSobreLoPagado() {
        Cotizacion anterior = Cotizacion.builder().ivaPct(19.0).totalAPagar(214_200.0).build();

        assertEquals(new BigDecimal("214200"), ComisionPlataforma.baseCotizacion(anterior, 214_200.0));
    }
}
