package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Montos de la factura (correo y PDF): el diseño puede cambiar, pero las líneas de Subtotal, IVA y
 * Total deben salir iguales. Cambiar cómo se muestra el IVA es una decisión aparte, no de diseño.
 */
class FacturaMontosTest {

    private static final double IVA = 0.19;

    static Usuario usuario() {
        return Usuario.builder().nombre("Laura Gómez").correo("laura@correo.co").identificacion("1020304050").build();
    }

    static Venta venta() {
        Venta v = new Venta();
        v.setId("66f0a1b2c3d4e5f6a7b81a2b");
        v.setFecha(LocalDateTime.of(2026, 10, 20, 15, 30));
        v.setEstado("PAGADA");
        DetalleVenta pastillas = new DetalleVenta();
        pastillas.setNombre("Pastillas de freno delanteras");
        pastillas.setCantidad(2);
        pastillas.setPrecioUnitario(100_000.0);
        pastillas.setSubtotal(200_000.0);
        DetalleVenta aceite = new DetalleVenta();
        aceite.setNombre("Aceite 10W-40 sintético 4 L");
        aceite.setCantidad(1);
        aceite.setPrecioUnitario(150_000.0);
        aceite.setSubtotal(150_000.0);
        v.setProductos(List.of(pastillas, aceite));
        Pago pago = new Pago();
        pago.setMetodo("WOMPI");
        pago.setMonto(416_500.0);
        v.setPagos(List.of(pago));
        v.setTotal(416_500.0);
        return v;
    }

    /** Sin etiquetas y con espacios normales: lo que lee el cliente. */
    private static String textoDe(String html) {
        return html.replaceAll("(?s)<style.*?</style>", " ").replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace(' ', ' ').replaceAll("\\s+", " ");
    }

    private static void contieneMontos(String texto) {
        assertTrue(texto.contains("Subtotal $ 350.000"), "Subtotal: " + texto);
        assertTrue(texto.contains("IVA (19%) $ 66.500"), "IVA: " + texto);
        assertTrue(texto.contains("Total $ 416.500"), "Total: " + texto);
        assertTrue(texto.contains("WOMPI $ 416.500"), "Forma de pago: " + texto);
        assertTrue(texto.contains("$ 100.000") && texto.contains("$ 200.000") && texto.contains("$ 150.000"), "Líneas: " + texto);
    }

    @Test
    void elCorreoDeLaFacturaMantieneSubtotalIvaYTotal() {
        contieneMontos(textoDe(EmailVentaBuilder.construir(usuario(), venta(), IVA)));
    }

    @Test
    void elPdfDeLaFacturaMantieneSubtotalIvaYTotal() throws Exception {
        PdfReader lector = new PdfReader(PdfVentaBuilder.construir(usuario(), venta(), IVA));
        String texto = new PdfTextExtractor(lector).getTextFromPage(1).replace(' ', ' ').replaceAll("\\s+", " ");
        assertTrue(texto.contains("IVA (19%)$ 66.500") || texto.contains("IVA (19%) $ 66.500"), "IVA: " + texto);
        assertTrue(texto.contains("Subtotal"), "Etiquetas: " + texto);
        assertTrue(texto.contains("$ 350.000") && texto.contains("$ 416.500"), "Montos: " + texto);
    }
}
