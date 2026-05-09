package com.drivemaster.drivemaster.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.DetalleCompra;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

public class PdfCompraBuilder {

    private static final Color ORANGE     = new Color(232, 69, 10);
    private static final Color BG_DARK    = new Color(13, 13, 13);
    private static final Color SURFACE    = new Color(20, 20, 20);
    private static final Color SURFACE2   = new Color(26, 26, 26);
    private static final Color TEXT_MAIN  = new Color(240, 240, 240);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color BORDER     = new Color(42, 42, 42);
    private static final Locale LOCALE_CO = new Locale("es", "CO");

    private static final float PAD = 32f;
    private static final float PW  = 595f; // A4 width

    public static byte[] construir(Compra compra,
                                   String proveedorNombre,
                                   String proveedorNit,
                                   String proveedorCorreo) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Calculamos altura real según productos
            int numProductos = compra.getProductos() != null ? compra.getProductos().size() : 0;

            float headerH = 75f;
            float secH    = 85f;
            float tblHdr  = 20f;
            float rowH    = 30f;
            float totH    = 60f;
            float footH   = 40f;
            float gap     = 12f;

            float pageH = headerH + gap
                        + secH    + gap * 2
                        + tblHdr  + (rowH * numProductos) + gap * 2
                        + totH    + gap
                        + footH;

            pageH = Math.max(pageH, 400f);

            com.lowagie.text.Rectangle pageSize = new com.lowagie.text.Rectangle(PW, pageH);
            Document doc = new Document(pageSize, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
            fmt.setMaximumFractionDigits(0);

            String poId  = "PO-" + (compra.getId() != null
                    ? compra.getId().substring(Math.max(0, compra.getId().length() - 5)).toUpperCase()
                    : "XXXXX");
            String fecha = compra.getFecha() != null
                    ? compra.getFecha().format(DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy HH:mm"))
                    : "—";

            double subtotal = compra.getProductos().stream()
                    .mapToDouble(d -> d.getSubtotal() != null ? d.getSubtotal() : 0).sum();

            BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.CP1252, false);
            BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
            PdfContentByte cb = writer.getDirectContent();

            float CW = PW - PAD * 2;

            // Fondo general
            drawRect(cb, 0, 0, PW, pageH, new Color(10, 10, 10));

            // ── HEADER ──
            float headerY = pageH - headerH;
            drawRect(cb, 0, headerY, PW, headerH, BG_DARK);
            drawRect(cb, 0, pageH - 3, PW, 3, ORANGE);

            cb.beginText();
            cb.setFontAndSize(bfBold, 22);
            cb.setColorFill(TEXT_MAIN);
            cb.setTextMatrix(PAD, headerY + 38);
            cb.showText("Drive");
            cb.setColorFill(ORANGE);
            cb.showText("Master");
            cb.endText();

            cb.beginText();
            cb.setFontAndSize(bf, 7);
            cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix(PAD, headerY + 22);
            cb.showText("REPUESTOS AUTOMOTRICES");
            cb.endText();

            String labelOI = "OFFICIAL INVOICE";
            float lwOI = bfBold.getWidthPoint(labelOI, 8);
            cb.beginText();
            cb.setFontAndSize(bfBold, 8);
            cb.setColorFill(ORANGE);
            cb.setTextMatrix(PW - PAD - lwOI, headerY + 52);
            cb.showText(labelOI);
            cb.endText();

            String idText = "FACTURA #" + poId;
            float lwId = bfBold.getWidthPoint(idText, 15);
            cb.beginText();
            cb.setFontAndSize(bfBold, 15);
            cb.setColorFill(TEXT_MAIN);
            cb.setTextMatrix(PW - PAD - lwId, headerY + 30);
            cb.showText(idText);
            cb.endText();

            // ── SECCIÓN PROVEEDOR ──
            float secY = headerY - gap;
            drawRect(cb, 0, secY - secH, PW, secH, SURFACE);
            drawLine(cb, 0, secY, PW, BORDER);
            drawLine(cb, 0, secY - secH, PW, BORDER);

            drawSmallLabel(cb, bf, "SUPPLIER INFORMATION", PAD, secY - 14, TEXT_MUTED);
            drawText(cb, bfBold, 11, safe(proveedorNombre), PAD, secY - 30, ORANGE);
            if (proveedorNit != null && !proveedorNit.isBlank())
                drawText(cb, bf, 8, "NIT: " + proveedorNit, PAD, secY - 44, TEXT_MUTED);
            if (proveedorCorreo != null && !proveedorCorreo.isBlank())
                drawText(cb, bf, 8, proveedorCorreo, PAD, secY - 57, TEXT_MUTED);

            float col2X = PW * 0.42f;
            drawSmallLabel(cb, bf, "SHIP TO",        col2X, secY - 14, TEXT_MUTED);
            drawText(cb, bfBold, 10, "DriveMaster",  col2X, secY - 30, TEXT_MAIN);
            drawText(cb, bf,      8, "Almacén Central", col2X, secY - 44, TEXT_MUTED);
            drawText(cb, bf,      8, "Colombia",     col2X, secY - 57, TEXT_MUTED);

            float col3X = PW * 0.72f;
            drawSmallLabel(cb, bf, "ISSUE DATE", col3X, secY - 14, TEXT_MUTED);
            drawText(cb, bfBold, 9, fecha,       col3X, secY - 30, TEXT_MAIN);

            // ── TABLA PRODUCTOS ──
            float tblTopY = secY - secH - gap;
            float wNom    = CW * 0.40f;
            float wCant   = CW * 0.14f;
            float wCost   = CW * 0.23f;

            drawRect(cb, 0, tblTopY - tblHdr, PW, tblHdr, SURFACE2);
            drawLine(cb, 0, tblTopY - tblHdr, PW, BORDER);
            drawTableHeader(cb, bf, PAD,                               tblTopY - 13, "PRODUCT NAME", TEXT_MUTED);
            drawTableHeaderCenter(cb, bf, PAD + wNom + wCant * 0.5f,  tblTopY - 13, "QUANTITY",     TEXT_MUTED);
            drawTableHeaderRight(cb, bf,  PAD + wNom + wCant + wCost, tblTopY - 13, "UNIT COST",    TEXT_MUTED);
            drawTableHeaderRight(cb, bf,  PW - PAD,                   tblTopY - 13, "SUBTOTAL",     TEXT_MUTED);

            float rowY = tblTopY - tblHdr;
            boolean odd = true;
            for (DetalleCompra d : compra.getProductos()) {
                Color rowBg = odd ? SURFACE : new Color(17, 17, 17);
                drawRect(cb, 0, rowY - rowH, PW, rowH, rowBg);
                drawLine(cb, 0, rowY - rowH, PW, new Color(30, 30, 30));

                drawText(cb, bfBold, 9, safe(d.getNombre()), PAD, rowY - 12, TEXT_MAIN);
                String sku = d.getProductoId() != null
                        ? "SKU: " + d.getProductoId().substring(
                            Math.max(0, d.getProductoId().length() - 8)).toUpperCase()
                        : "SKU: —";
                drawText(cb, bf, 7, sku, PAD, rowY - 23, TEXT_MUTED);

                drawTextCenter(cb, bfBold, 10,
                        String.format("%02d", d.getCantidad()),
                        PAD + wNom + wCant * 0.5f, rowY - 17, TEXT_MAIN);
                drawTextRight(cb, bf, 9,
                        fmt.format(d.getCosto() != null ? d.getCosto() : 0),
                        PAD + wNom + wCant + wCost, rowY - 17, TEXT_MUTED);
                drawTextRight(cb, bfBold, 9,
                        fmt.format(d.getSubtotal() != null ? d.getSubtotal() : 0),
                        PW - PAD, rowY - 17, ORANGE);

                rowY -= rowH;
                odd = !odd;
            }
            drawLine(cb, 0, rowY, PW, BORDER);

            // ── TOTALES ──
            float totY  = rowY - gap;
            float totW  = CW * 0.42f;
            float totX  = PW - PAD - totW;

            drawRect(cb, totX, totY - totH, totW, totH, SURFACE2);
            drawBorder(cb, totX, totY - totH, totW, totH, BORDER);
            drawRect(cb, totX, totY - totH, 3, totH, ORANGE);

            drawText(cb, bf, 9, "Subtotal", totX + 12, totY - 16, TEXT_MUTED);
            drawTextRight(cb, bf, 9, fmt.format(subtotal), totX + totW - 10, totY - 16, TEXT_MAIN);
            drawLine(cb, totX + 10, totY - 30, totW - 20, BORDER);
            drawText(cb, bfBold, 12, "TOTAL DUE", totX + 12, totY - 46, TEXT_MAIN);
            drawTextRight(cb, bfBold, 13,
                    fmt.format(compra.getTotal() != null ? compra.getTotal() : subtotal),
                    totX + totW - 10, totY - 46, ORANGE);

            // ── FOOTER — pegado justo debajo de los totales ──
            float footY = totY - totH - gap;
            drawRect(cb, 0, footY - footH, PW, footH, BG_DARK);
            drawLine(cb, 0, footY, PW, BORDER);

            String footTxt = "Documento oficial de compra — DriveMaster Repuestos Automotrices";
            float lwF = bf.getWidthPoint(footTxt, 7.5f);
            cb.beginText();
            cb.setFontAndSize(bf, 7.5f);
            cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix((PW - lwF) / 2, footY - footH + 24);
            cb.showText(footTxt);
            cb.endText();

            String copy = "© 2026 DriveMaster · Todos los derechos reservados";
            float lwC = bf.getWidthPoint(copy, 7);
            cb.beginText();
            cb.setFontAndSize(bf, 7);
            cb.setColorFill(new Color(68, 68, 68));
            cb.setTextMatrix((PW - lwC) / 2, footY - footH + 11);
            cb.showText(copy);
            cb.endText();

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de compra: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void drawRect(PdfContentByte cb, float x, float y, float w, float h, Color c) {
        cb.setColorFill(c); cb.rectangle(x, y, w, h); cb.fill();
    }
    private static void drawBorder(PdfContentByte cb, float x, float y, float w, float h, Color c) {
        cb.setColorStroke(c); cb.setLineWidth(0.5f); cb.rectangle(x, y, w, h); cb.stroke();
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