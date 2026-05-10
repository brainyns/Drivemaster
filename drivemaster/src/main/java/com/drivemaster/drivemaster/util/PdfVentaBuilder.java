package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Cliente;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Venta;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PdfVentaBuilder {

    // ── Paleta blanca / profesional ───────────────────────────────────────────
    private static final Color ORANGE      = new Color(232, 69, 10);
    private static final Color BG_PAGE     = new Color(243, 244, 246);  // gris claro de página
    private static final Color BG_WHITE    = new Color(255, 255, 255);  // fondo principal
    private static final Color BG_SURFACE  = new Color(249, 250, 251);  // filas alternas / paneles
    private static final Color BG_HEADER   = new Color(255, 255, 255);  // cabecera
    private static final Color TEXT_DARK   = new Color(17,  24,  39);   // títulos
    private static final Color TEXT_BODY   = new Color(55,  65,  81);   // texto normal
    private static final Color TEXT_MUTED  = new Color(107, 114, 128);  // etiquetas / notas
    private static final Color TEXT_LIGHT  = new Color(156, 163, 175);  // placeholders
    private static final Color GREEN_BG    = new Color(209, 250, 229);  // badge PAGADA fondo
    private static final Color GREEN_TEXT  = new Color(6,   95,  70);   // badge PAGADA texto
    private static final Color BORDER      = new Color(229, 231, 235);  // bordes suaves
    private static final Color BORDER_ROW  = new Color(243, 244, 246);  // separador filas

    private static final Locale LOCALE_CO = new Locale("es", "CO");
    private static final float  PAD = 32f;
    private static final float  PW  = PageSize.A4.getWidth();
    private static final float  CW  = PW - PAD * 2;

    public static byte[] construir(Cliente cliente, Venta venta, double iva) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            int numProductos = venta.getProductos() != null ? venta.getProductos().size() : 0;

            float headerH = 75f;
            float secH    = 78f;
            float tblHdr  = 20f;
            float rowH    = 24f;
            float botH    = 95f;
            float footH   = 40f;
            float gap     = 12f;

            float totalH = headerH + gap
                         + secH    + gap * 2
                         + tblHdr  + (rowH * numProductos) + gap * 2
                         + botH    + gap
                         + footH;

            float pageH = Math.max(totalH, 400f);

            com.lowagie.text.Rectangle pageSize = new com.lowagie.text.Rectangle(PW, pageH);
            Document doc = new Document(pageSize, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
            fmt.setMaximumFractionDigits(0);

            String idCorto = venta.getId() != null
                    ? venta.getId().substring(Math.max(0, venta.getId().length() - 8)).toUpperCase()
                    : "XXXXXXXX";
            String fecha = venta.getFecha() != null
                    ? venta.getFecha().format(DateTimeFormatter.ofPattern(
                        "dd 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "CO")))
                    : "—";

            double subtotalBruto = venta.getProductos().stream()
                    .mapToDouble(d -> d.getSubtotal() != null ? d.getSubtotal() : 0).sum();
            double ivaValor = subtotalBruto * iva;
            int    ivaPct   = (int) Math.round(iva * 100);

            BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.CP1252, false);
            BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
            PdfContentByte cb = writer.getDirectContent();

            // Fondo general de página (gris muy claro)
            drawRect(cb, 0, 0, PW, pageH, BG_PAGE);

            // ── HEADER (fondo blanco + línea naranja arriba) ──
            float headerY = pageH - headerH;
            drawRect(cb, 0, headerY, PW, headerH, BG_HEADER);
            drawRect(cb, 0, pageH - 3, PW, 3, ORANGE);
            drawLine(cb, 0, headerY, PW, BORDER);

            cb.beginText();
            cb.setFontAndSize(bfBold, 22);
            cb.setColorFill(TEXT_DARK);
            cb.setTextMatrix(PAD, headerY + 38);
            cb.showText("Drive");
            cb.setColorFill(ORANGE);
            cb.showText("Master");
            cb.endText();

            cb.beginText();
            cb.setFontAndSize(bf, 7);
            cb.setColorFill(TEXT_LIGHT);
            cb.setTextMatrix(PAD, headerY + 22);
            cb.showText("REPUESTOS AUTOMOTRICES");
            cb.endText();

            String labelFactura = "FACTURA DE VENTA";
            float lwLabel = bfBold.getWidthPoint(labelFactura, 8);
            cb.beginText();
            cb.setFontAndSize(bfBold, 8);
            cb.setColorFill(ORANGE);
            cb.setTextMatrix(PW - PAD - lwLabel, headerY + 52);
            cb.showText(labelFactura);
            cb.endText();

            String idText = "#" + idCorto;
            float lwId = bfBold.getWidthPoint(idText, 20);
            cb.beginText();
            cb.setFontAndSize(bfBold, 20);
            cb.setColorFill(TEXT_DARK);
            cb.setTextMatrix(PW - PAD - lwId, headerY + 28);
            cb.showText(idText);
            cb.endText();

            // ── SECCIÓN CLIENTE / FECHA ──
            float secY = headerY - gap;
            drawRect(cb, 0, secY - secH, PW, secH, BG_SURFACE);
            drawLine(cb, 0, secY, PW, BORDER);
            drawLine(cb, 0, secY - secH, PW, BORDER);

            drawSmallLabel(cb, bf, "CLIENTE", PAD, secY - 14, TEXT_MUTED);
            drawText(cb, bfBold, 11, cliente.getNombre() != null ? cliente.getNombre() : "—",
                    PAD, secY - 30, TEXT_DARK);
            drawText(cb, bf, 8, cliente.getCorreo() != null ? cliente.getCorreo() : "",
                    PAD, secY - 44, TEXT_MUTED);
            if (cliente.getIdentificacion() != null && !cliente.getIdentificacion().isBlank())
                drawText(cb, bf, 8, "CC: " + cliente.getIdentificacion(), PAD, secY - 57, TEXT_LIGHT);

            float col2X = PW * 0.55f;
            drawSmallLabel(cb, bf, "FECHA DE EMISIÓN", col2X, secY - 14, TEXT_MUTED);
            drawText(cb, bf, 9, fecha, col2X, secY - 30, TEXT_BODY);

            String estado = venta.getEstado() != null ? venta.getEstado() : "PAGADA";
            float badgeW = bfBold.getWidthPoint(estado, 8) + 16;
            drawRoundRect(cb, col2X, secY - 62, badgeW, 18, GREEN_BG);
            drawText(cb, bfBold, 8, estado, col2X + 8, secY - 55, GREEN_TEXT);

            // ── TABLA PRODUCTOS ──
            float tblTopY = secY - secH - gap;
            float wNom    = CW * 0.42f;
            float wCant   = CW * 0.12f;
            float wPU     = CW * 0.22f;

            // Cabecera tabla
            drawRect(cb, 0, tblTopY - tblHdr, PW, tblHdr, BG_SURFACE);
            drawLine(cb, 0, tblTopY,         PW, BORDER);
            drawLine(cb, 0, tblTopY - tblHdr, PW, BORDER);
            drawTableHeader(cb, bf, PAD,                               tblTopY - 13, "PRODUCTO",     TEXT_MUTED);
            drawTableHeaderCenter(cb, bf, PAD + wNom + wCant * 0.5f,  tblTopY - 13, "CANT.",        TEXT_MUTED);
            drawTableHeaderRight(cb, bf,  PAD + wNom + wCant + wPU,   tblTopY - 13, "PRECIO UNIT.", TEXT_MUTED);
            drawTableHeaderRight(cb, bf,  PW - PAD,                   tblTopY - 13, "SUBTOTAL",     TEXT_MUTED);

            float rowY = tblTopY - tblHdr;
            boolean odd = true;
            for (DetalleVenta d : venta.getProductos()) {
                Color rowBg = odd ? BG_WHITE : BG_SURFACE;
                drawRect(cb, 0, rowY - rowH, PW, rowH, rowBg);
                drawLine(cb, 0, rowY - rowH, PW, BORDER_ROW);

                drawText(cb, bf,     9, safe(d.getNombre()),                      PAD,                          rowY - 15, TEXT_BODY);
                drawTextCenter(cb, bf, 9, String.valueOf(d.getCantidad()),         PAD + wNom + wCant * 0.5f,   rowY - 15, TEXT_MUTED);
                drawTextRight(cb, bf,  9, fmt.format(d.getPrecioUnitario()),       PAD + wNom + wCant + wPU,    rowY - 15, TEXT_MUTED);
                drawTextRight(cb, bfBold, 9, fmt.format(d.getSubtotal()),          PW - PAD,                   rowY - 15, ORANGE);

                rowY -= rowH;
                odd = !odd;
            }
            drawLine(cb, 0, rowY, PW, BORDER);

            // ── TOTALES + PAGOS ──
            float botY  = rowY - gap;
            float halfW = CW * 0.48f;

            // Panel pagos — izquierda (fondo blanco con borde)
            drawRect(cb, PAD, botY - botH, halfW, botH, BG_WHITE);
            drawBorder(cb, PAD, botY - botH, halfW, botH, BORDER);
            drawSmallLabel(cb, bf, "FORMA DE PAGO", PAD + 12, botY - 14, TEXT_MUTED);

            float pagoY = botY - 32;
            for (Pago p : venta.getPagos()) {
                drawText(cb, bf, 9, safe(p.getMetodo()), PAD + 12, pagoY, TEXT_MUTED);
                drawTextRight(cb, bfBold, 9, fmt.format(p.getMonto()),
                        PAD + halfW - 10, pagoY, TEXT_DARK);
                pagoY -= 18;
            }

            // Panel totales — derecha (fondo gris claro + borde naranja izquierdo)
            float panX = PW - PAD - halfW;
            drawRect(cb, panX, botY - botH, halfW, botH, BG_SURFACE);
            drawBorder(cb, panX, botY - botH, halfW, botH, BORDER);
            drawRect(cb, panX, botY - botH, 3, botH, ORANGE);

            float totY2 = botY - 18;
            drawText(cb, bf, 9, "Subtotal", panX + 12, totY2, TEXT_MUTED);
            drawTextRight(cb, bf, 9, fmt.format(subtotalBruto), panX + halfW - 10, totY2, TEXT_BODY);
            totY2 -= 18;
            drawText(cb, bf, 9, "IVA (" + ivaPct + "%)", panX + 12, totY2, TEXT_MUTED);
            drawTextRight(cb, bf, 9, fmt.format(ivaValor), panX + halfW - 10, totY2, TEXT_BODY);
            totY2 -= 8;
            drawLine(cb, panX + 10, totY2, halfW - 20, BORDER);
            totY2 -= 18;
            drawText(cb, bfBold, 12, "Total", panX + 12, totY2, TEXT_DARK);
            drawTextRight(cb, bfBold, 14, fmt.format(venta.getTotal()),
                    panX + halfW - 10, totY2, ORANGE);

            // ── FOOTER ──
            float footY = botY - botH - gap;
            drawRect(cb, 0, footY - footH, PW, footH, BG_SURFACE);
            drawLine(cb, 0, footY, PW, BORDER);

            String footTxt = "Este documento es una confirmación electrónica de su compra en DriveMaster.";
            float lwFoot = bf.getWidthPoint(footTxt, 7.5f);
            cb.beginText();
            cb.setFontAndSize(bf, 7.5f);
            cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix((PW - lwFoot) / 2, footY - footH + 24);
            cb.showText(footTxt);
            cb.endText();

            String copy = "© 2026 DriveMaster · Todos los derechos reservados";
            float lwCopy = bf.getWidthPoint(copy, 7);
            cb.beginText();
            cb.setFontAndSize(bf, 7);
            cb.setColorFill(TEXT_LIGHT);
            cb.setTextMatrix((PW - lwCopy) / 2, footY - footH + 11);
            cb.showText(copy);
            cb.endText();

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de venta: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void drawRect(PdfContentByte cb, float x, float y, float w, float h, Color c) {
        cb.setColorFill(c); cb.rectangle(x, y, w, h); cb.fill();
    }
    private static void drawBorder(PdfContentByte cb, float x, float y, float w, float h, Color c) {
        cb.setColorStroke(c); cb.setLineWidth(0.5f); cb.rectangle(x, y, w, h); cb.stroke();
    }
    private static void drawRoundRect(PdfContentByte cb, float x, float y, float w, float h, Color fill) {
        cb.setColorFill(fill); cb.roundRectangle(x, y, w, h, 8); cb.fill();
    }
    private static void drawLine(PdfContentByte cb, float x, float y, float w, Color c) {
        cb.setColorStroke(c); cb.setLineWidth(0.5f);
        cb.moveTo(x, y); cb.lineTo(x + w, y); cb.stroke();
    }
    private static void drawText(PdfContentByte cb, BaseFont bf, float size,
                                  String text, float x, float y, Color color) {
        cb.beginText(); cb.setFontAndSize(bf, size); cb.setColorFill(color);
        cb.setTextMatrix(x, y); cb.showText(safe(text)); cb.endText();
    }
    private static void drawTextRight(PdfContentByte cb, BaseFont bf, float size,
                                       String text, float rx, float y, Color color) {
        float w = bf.getWidthPoint(safe(text), size);
        drawText(cb, bf, size, text, rx - w, y, color);
    }
    private static void drawTextCenter(PdfContentByte cb, BaseFont bf, float size,
                                        String text, float cx, float y, Color color) {
        float w = bf.getWidthPoint(safe(text), size);
        drawText(cb, bf, size, text, cx - w / 2, y, color);
    }
    private static void drawSmallLabel(PdfContentByte cb, BaseFont bf,
                                        String text, float x, float y, Color color) {
        cb.beginText(); cb.setFontAndSize(bf, 7.5f); cb.setColorFill(color);
        cb.setTextMatrix(x, y); cb.showText(text); cb.endText();
    }
    private static void drawTableHeader(PdfContentByte cb, BaseFont bf,
                                         float x, float y, String text, Color color) {
        cb.beginText(); cb.setFontAndSize(bf, 7); cb.setColorFill(color);
        cb.setTextMatrix(x, y); cb.showText(text); cb.endText();
    }
    private static void drawTableHeaderRight(PdfContentByte cb, BaseFont bf,
                                              float rx, float y, String text, Color color) {
        float w = bf.getWidthPoint(text, 7);
        drawTableHeader(cb, bf, rx - w, y, text, color);
    }
    private static void drawTableHeaderCenter(PdfContentByte cb, BaseFont bf,
                                               float cx, float y, String text, Color color) {
        float w = bf.getWidthPoint(text, 7);
        drawTableHeader(cb, bf, cx - w / 2, y, text, color);
    }
    private static String safe(String s) { return s != null ? s : "—"; }
}