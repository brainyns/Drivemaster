package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.Taller;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PDF de una cotización del marketplace, con el mismo contenido que su impresión (CotizacionDetalle): datos del taller,
 * vehículo, repuestos, mano de obra, IVA desglosado, totales y vigencia. Con OpenPDF (LGPL/MPL, libre para uso comercial).
 * Flujo de párrafos y tablas en vez de coordenadas fijas: una cotización larga pasa sola a la página siguiente.
 * Los montos vienen calculados en la cotización; aquí solo se escriben.
 */
public final class PdfCotizacionBuilder {

    private static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", LOCALE_CO);

    private static final Color TEXTO = new Color(55, 65, 81);
    private static final Color TENUE = new Color(107, 114, 128);
    private static final Color BORDE = new Color(229, 231, 235);
    private static final Color FONDO = new Color(249, 250, 251);

    private static final Font TITULO = new Font(Font.HELVETICA, 18, Font.BOLD, TEXTO);
    private static final Font SECCION = new Font(Font.HELVETICA, 11, Font.BOLD, TEXTO);
    private static final Font NORMAL = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXTO);
    private static final Font NEGRITA = new Font(Font.HELVETICA, 10, Font.BOLD, TEXTO);
    private static final Font META = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TENUE);

    private PdfCotizacionBuilder() {
    }

    /** Una fila de los totales: texto y monto (negativo para lo que se descuenta). */
    public record Linea(String texto, double monto) {}

    /**
     * Totales en el orden de la pantalla. Sin anticipo, el total y el total a pagar son la misma cifra: solo queda el
     * segundo. El IVA va en una línea por porcentaje (ver {@link #lineasIva}).
     */
    public static List<Linea> totales(Cotizacion c) {
        List<Linea> lineas = new ArrayList<>();
        lineas.add(new Linea("Subtotal", valor(c.getSubtotal())));
        lineas.addAll(lineasIva(c));
        double anticipo = valor(c.getAnticipoDiagnostico());
        if (anticipo > 0) {
            lineas.add(new Linea("Total", valor(c.getTotal())));
            lineas.add(new Linea("Diagnóstico ya pagado", -anticipo));
        }
        lineas.add(new Linea("Total a pagar", valor(c.getTotalAPagar())));
        return lineas;
    }

    /** IVA de la cotización: una sola línea con su porcentaje. */
    public static List<Linea> lineasIva(Cotizacion c) {
        return List.of(new Linea("IVA (" + pct(c.getIvaPct()) + ")", valor(c.getIva())));
    }

    public static byte[] construir(Cotizacion c, Taller taller) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.LETTER, 48, 48, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Cotización v" + c.getVersion() + " · " + texto(c.getTallerNombre()));
            doc.addCreator(BrandConfig.nombre());
            doc.open();

            encabezado(doc, c);
            doc.add(datos(c, taller));

            List<CotizacionItem> repuestos = c.getItems().stream().filter(i -> CotizacionItem.REPUESTO.equals(i.getTipo())).toList();
            List<CotizacionItem> manoObra = c.getItems().stream().filter(i -> !CotizacionItem.REPUESTO.equals(i.getTipo())).toList();
            if (!repuestos.isEmpty()) {
                doc.add(seccion("Repuestos"));
                doc.add(tablaItems(repuestos, true));
            }
            if (!manoObra.isEmpty()) {
                doc.add(seccion("Mano de obra"));
                doc.add(tablaItems(manoObra, false));
            }
            doc.add(tablaTotales(totales(c)));
            pie(doc, c);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de la cotización " + c.getId(), e);
        }
    }

    // ─── Partes del documento ────────────────────────────────────────────────

    private static void encabezado(Document doc, Cotizacion c) throws Exception {
        byte[] logo = BrandConfig.logoParaPdf();
        if (logo != null) {
            Image img = Image.getInstance(logo);
            img.scaleToFit(120, 36);
            doc.add(img);
        } else {
            Paragraph marca = new Paragraph();
            marca.add(new Chunk(BrandConfig.nombre(), new Font(Font.HELVETICA, 14, Font.BOLD, BrandConfig.colorTextoAwt())));
            marca.add(new Chunk(".", new Font(Font.HELVETICA, 14, Font.BOLD, BrandConfig.colorPrimarioAwt())));
            doc.add(marca);
        }
        Paragraph titulo = new Paragraph("Cotización v" + c.getVersion(), TITULO);
        titulo.setSpacingBefore(10);
        doc.add(titulo);
        doc.add(new Paragraph("Emitida el " + fecha(c.getFechaEmision()) + " · Referencia " + referencia(c), META));
    }

    /** Taller y vehículo, lado a lado. */
    private static PdfPTable datos(Cotizacion c, Taller taller) {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(14);
        tabla.setSpacingAfter(6);

        Phrase deTaller = new Phrase();
        deTaller.add(new Chunk("Taller\n", META));
        deTaller.add(new Chunk(texto(taller != null ? taller.getNombre() : c.getTallerNombre()) + "\n", NEGRITA));
        if (taller != null) {
            String direccion = unir(", ", taller.getDireccion(), taller.getBarrio(), taller.getCiudad());
            if (!direccion.isEmpty()) deTaller.add(new Chunk(direccion + "\n", NORMAL));
            if (taller.getTelefono() != null && !taller.getTelefono().isBlank()) {
                deTaller.add(new Chunk("Tel. " + taller.getTelefono(), NORMAL));
            }
        }
        Phrase deVehiculo = new Phrase();
        deVehiculo.add(new Chunk("Vehículo\n", META));
        deVehiculo.add(new Chunk(texto(c.getVehiculoDescripcion()).isEmpty() ? "Vehículo" : c.getVehiculoDescripcion(), NEGRITA));

        tabla.addCell(caja(deTaller));
        tabla.addCell(caja(deVehiculo));
        return tabla;
    }

    private static Paragraph seccion(String nombre) {
        Paragraph p = new Paragraph(nombre, SECCION);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    /** Una fila por ítem: descripción con su detalle debajo y el valor a la derecha. */
    private static PdfPTable tablaItems(List<CotizacionItem> items, boolean repuestos) throws Exception {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{4.2f, 1.3f});
        for (CotizacionItem i : items) {
            Phrase desc = new Phrase();
            desc.add(new Chunk((repuestos ? i.getCantidad() + " × " : "") + texto(i.getDescripcion()), NORMAL));
            String detalle = repuestos ? detalleRepuesto(i)
                    : CotizacionItem.POR_HORAS.equals(i.getModalidad()) ? horas(i.getHoras()) + " h × " + moneda(i.getPrecioUnitario()) : "";
            if (!detalle.isEmpty()) desc.add(new Chunk("\n" + detalle, META));
            tabla.addCell(fila(desc, Element.ALIGN_LEFT));
            tabla.addCell(fila(new Phrase(moneda(i.getSubtotal()), NORMAL), Element.ALIGN_RIGHT));
        }
        return tabla;
    }

    private static PdfPTable tablaTotales(List<Linea> lineas) throws Exception {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(55);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setWidths(new float[]{2.6f, 1.6f});
        tabla.setSpacingBefore(14);
        for (int n = 0; n < lineas.size(); n++) {
            Linea l = lineas.get(n);
            boolean ultima = n == lineas.size() - 1;
            Font font = ultima ? new Font(Font.HELVETICA, 12, Font.BOLD, TEXTO) : NORMAL;
            // Guion y no "−": Helvetica (WinAnsi) no tiene el signo menos tipográfico
            String monto = l.monto() < 0 ? "- " + moneda(-l.monto()) : moneda(l.monto());
            PdfPCell texto = fila(new Phrase(l.texto(), font), Element.ALIGN_LEFT);
            PdfPCell valor = fila(new Phrase(monto, font), Element.ALIGN_RIGHT);
            if (ultima) {
                texto.setBackgroundColor(FONDO);
                valor.setBackgroundColor(FONDO);
            }
            tabla.addCell(texto);
            tabla.addCell(valor);
        }
        return tabla;
    }

    private static void pie(Document doc, Cotizacion c) throws Exception {
        List<String> partes = new ArrayList<>();
        if (c.getTiempoEstimadoHoras() != null) partes.add("Tiempo estimado del trabajo: " + c.getTiempoEstimadoHoras() + " h");
        if (c.getDiasEntregaRepuestos() != null) partes.add("Repuestos por encargo: hasta " + c.getDiasEntregaRepuestos() + " días");
        if (c.getVigenciaHasta() != null) partes.add("Válida hasta el " + fecha(c.getVigenciaHasta()));
        Paragraph p = new Paragraph(String.join(" · ", partes), NORMAL);
        p.setSpacingBefore(16);
        doc.add(p);
        if (c.getObservaciones() != null && !c.getObservaciones().isBlank()) {
            Paragraph nota = new Paragraph("Nota del taller: " + c.getObservaciones(), NORMAL);
            nota.setSpacingBefore(6);
            doc.add(nota);
        }
        Paragraph cop = new Paragraph("Valores en pesos colombianos (COP). Generado por " + BrandConfig.nombre() + ".", META);
        cop.setSpacingBefore(18);
        doc.add(cop);
    }

    // ─── Celdas ──────────────────────────────────────────────────────────────

    private static PdfPCell caja(Phrase contenido) {
        PdfPCell celda = new PdfPCell(contenido);
        celda.setBorderColor(BORDE);
        celda.setPadding(10);
        celda.setLeading(0, 1.35f);
        return celda;
    }

    private static PdfPCell fila(Phrase contenido, int alineacion) {
        PdfPCell celda = new PdfPCell(contenido);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(BORDE);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setPaddingTop(6);
        celda.setPaddingBottom(7);
        celda.setLeading(0, 1.3f);
        return celda;
    }

    // ─── Formato ─────────────────────────────────────────────────────────────

    private static String detalleRepuesto(CotizacionItem i) {
        String detalle = unir(" · ", i.getMarca(), i.getReferencia(), i.getTiendaNombre());
        if ("ENCARGO".equals(i.getDisponibilidad())) {
            detalle = unir(" · ", detalle, "Encargo" + (i.getDiasEntrega() != null ? " (" + i.getDiasEntrega() + " días)" : ""));
        }
        return detalle;
    }

    /** Los últimos 6 caracteres del id, como la referencia corta de la pantalla. */
    private static String referencia(Cotizacion c) {
        String id = c.getId() != null ? c.getId() : "";
        return id.substring(Math.max(0, id.length() - 6)).toUpperCase(LOCALE_CO);
    }

    static String moneda(Double valor) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
        fmt.setMaximumFractionDigits(0);
        return fmt.format(valor(valor));
    }

    static String pct(Double pct) {
        if (pct == null) return "";
        return (pct == Math.floor(pct) ? String.valueOf(pct.intValue()) : String.valueOf(pct).replace('.', ',')) + " %";
    }

    private static String horas(Double h) {
        if (h == null) return "0";
        return h == Math.floor(h) ? String.valueOf(h.intValue()) : String.valueOf(h).replace('.', ',');
    }

    private static String fecha(LocalDateTime f) {
        return f != null ? f.format(FECHA_HORA) : "";
    }

    private static double valor(Double v) {
        return v != null ? v : 0;
    }

    private static String texto(String s) {
        return s != null ? s.trim() : "";
    }

    private static String unir(String separador, String... partes) {
        List<String> llenas = new ArrayList<>();
        for (String p : partes) if (p != null && !p.isBlank()) llenas.add(p.trim());
        return String.join(separador, llenas);
    }
}
