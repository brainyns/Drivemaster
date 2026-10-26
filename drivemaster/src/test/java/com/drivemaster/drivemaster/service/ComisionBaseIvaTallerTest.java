package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base de la comisión con el IVA por taller: siempre el precio sin IVA, sea cual sea el IVA del taller. Diagnóstico +
 * cotización suman la comisión del subtotal completo (el anticipo no se cuenta dos veces).
 */
class ComisionBaseIvaTallerTest {

    private static final double IVA = 19;

    private static OrdenServicio diagnostico(double base, double pct) {
        return OrdenServicio.builder().tarifaDiagnosticoBase(base).ivaDiagnosticoPct(pct)
                .tarifaDiagnostico(base + Math.round(base * pct / 100)).build();
    }

    @Test
    void elDiagnosticoCobraSobreLaBaseResponsableONo() {
        assertEquals(new BigDecimal("50000"), ComisionPlataforma.baseDiagnostico(diagnostico(50_000, 19), 59_500.0, IVA));
        assertEquals(new BigDecimal("50000"), ComisionPlataforma.baseDiagnostico(diagnostico(50_000, 0), 50_000.0, IVA));
    }

    @Test
    void elDiagnosticoAnteriorTraiaElIvaIncluidoYSeLeQuita() {
        OrdenServicio anterior = OrdenServicio.builder().tarifaDiagnostico(59_500.0).build();

        assertEquals(new BigDecimal("50000"), ComisionPlataforma.baseDiagnostico(anterior, 59_500.0, IVA));
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
    void laCotizacionDeUnTallerNoResponsableCobraSobreElSubtotal() {
        Cotizacion c = cotizacion(100_000, 80_000, 0, 0);

        // Pagado 199.000 (repuestos con IVA + mano de obra sin IVA): quitarle un 19 % a todo daría 167.227
        assertEquals(new BigDecimal("180000"), ComisionPlataforma.baseCotizacion(c, new OrdenServicio(), c.getTotalAPagar(), IVA));
    }

    @Test
    void laCotizacionDescuentaLaBaseDelDiagnosticoYaPagado() {
        // Responsable: diagnóstico 50.000 + 19 % = 59.500 pagado antes
        Cotizacion responsable = cotizacion(100_000, 80_000, 19, 59_500);
        assertEquals(new BigDecimal("130000"),
                ComisionPlataforma.baseCotizacion(responsable, diagnostico(50_000, 19), responsable.getTotalAPagar(), IVA));

        // No responsable: diagnóstico 50.000 sin IVA
        Cotizacion noResponsable = cotizacion(100_000, 80_000, 0, 50_000);
        assertEquals(new BigDecimal("130000"),
                ComisionPlataforma.baseCotizacion(noResponsable, diagnostico(50_000, 0), noResponsable.getTotalAPagar(), IVA));
    }

    @Test
    void conUnaSolaTarifaDaLoMismoQueAntes() {
        Cotizacion c = cotizacion(100_000, 80_000, 19, 0);
        BigDecimal antes = ComisionPlataforma.baseSinIva(c.getTotalAPagar(), IVA);

        assertEquals(antes, ComisionPlataforma.baseCotizacion(c, new OrdenServicio(), c.getTotalAPagar(), IVA));
    }

    @Test
    void lasCotizacionesAnterioresSiguenConSuIvaPctUnico() {
        Cotizacion anterior = Cotizacion.builder().ivaPct(19.0).subtotal(180_000.0).totalAPagar(214_200.0).build();

        assertEquals(new BigDecimal("180000"), ComisionPlataforma.baseCotizacion(anterior, null, 214_200.0, IVA));
    }
}
