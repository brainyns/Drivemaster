package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.Taller;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PDF de la cotización: mismo contenido que la impresión y totales en el orden de la pantalla. */
class PdfCotizacionBuilderTest {

    private static Cotizacion cotizacion(double anticipo) {
        List<CotizacionItem> items = new ArrayList<>();
        items.add(CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).descripcion("Pastillas de freno").cantidad(2)
                .marca("Bosch").referencia("BP-123").tiendaNombre("Repuestos El Sol").disponibilidad("ENCARGO").diasEntrega(3)
                .precioUnitario(45_000.0).subtotal(90_000.0).build());
        items.add(CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).descripcion("Cambio de pastillas")
                .modalidad(CotizacionItem.POR_HORAS).horas(1.5).precioUnitario(40_000.0).subtotal(60_000.0).build());
        return Cotizacion.builder().id("abc123def456").version(2).estado(Cotizacion.APROBADA)
                .tallerNombre("AutoMax").vehiculoDescripcion("Mazda 3 2019 · ABC123").items(items)
                .subtotalRepuestos(90_000.0).subtotalManoObra(60_000.0).subtotal(150_000.0)
                .ivaPct(19.0).ivaRepuestos(17_100.0).ivaManoObra(11_400.0).iva(28_500.0).total(178_500.0)
                .anticipoDiagnostico(anticipo).totalAPagar(178_500.0 - anticipo)
                .tiempoEstimadoHoras(4).diasEntregaRepuestos(3).observaciones("Incluye revisión de discos")
                .fechaEmision(LocalDateTime.of(2026, 10, 1, 9, 30)).vigenciaHasta(LocalDateTime.of(2026, 10, 8, 9, 30))
                .build();
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder sb = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int p = 1; p <= reader.getNumberOfPages(); p++) sb.append(extractor.getTextFromPage(p)).append('\n');
        reader.close();
        return sb.toString();
    }

    @Test
    void elPdfTraeTallerVehiculoItemsIvaTotalesYVigencia() throws Exception {
        Taller taller = Taller.builder().id("t1").nombre("AutoMax").direccion("Cra 7 # 45-10").barrio("Chapinero")
                .ciudad("Bogotá").telefono("3001234567").build();

        byte[] pdf = PdfCotizacionBuilder.construir(cotizacion(50_000), taller);
        String t = texto(pdf);

        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
        for (String esperado : List.of("Cotización v2", "AutoMax", "Cra 7 # 45-10, Chapinero, Bogotá", "3001234567",
                "Mazda 3 2019", "2 × Pastillas de freno", "Bosch · BP-123 · Repuestos El Sol · Encargo (3 días)",
                "Cambio de pastillas", "1,5 h ×", "IVA (19 %)", "28.500", "Diagnóstico ya pagado", "Total a pagar",
                "128.500", "Válida hasta el 8 de octubre de 2026", "Nota del taller: Incluye revisión de discos")) {
            assertTrue(t.contains(esperado), "falta en el PDF: " + esperado + "\n---\n" + t);
        }
    }

    @Test
    void sinAnticipoSoloQuedaElTotalAPagar() {
        var lineas = PdfCotizacionBuilder.totales(cotizacion(0));

        assertEquals(List.of("Subtotal", "IVA (19 %)", "Total a pagar"), lineas.stream().map(l -> l.texto()).toList());
        assertEquals(178_500.0, lineas.get(2).monto());
    }

    @Test
    void conAnticipoElDiagnosticoSeDescuentaDelTotal() {
        var lineas = PdfCotizacionBuilder.totales(cotizacion(50_000));

        assertEquals(List.of("Subtotal", "IVA (19 %)", "Total", "Diagnóstico ya pagado", "Total a pagar"),
                lineas.stream().map(l -> l.texto()).toList());
        assertEquals(-50_000.0, lineas.get(3).monto());
        assertEquals(128_500.0, lineas.get(4).monto());
    }

    @Test
    void sinTallerGuardadoUsaElNombreDeLaCotizacion() throws Exception {
        String t = texto(PdfCotizacionBuilder.construir(cotizacion(0), null));

        assertTrue(t.contains("AutoMax"));
    }
}
