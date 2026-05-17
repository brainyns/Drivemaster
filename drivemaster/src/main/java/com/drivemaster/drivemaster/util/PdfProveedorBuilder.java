package com.drivemaster.drivemaster.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.drivemaster.drivemaster.model.Proveedor;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

public class PdfProveedorBuilder {

    private static final Color ORANGE    = new Color(232, 69, 10);
    private static final Color BG_PAGE   = new Color(243, 244, 246);
    private static final Color BG_WHITE  = new Color(255, 255, 255);
    private static final Color BG_STRIPE = new Color(249, 250, 251);
    private static final Color TEXT_DARK = new Color(17, 24, 39);
    private static final Color TEXT_BODY = new Color(55, 65, 81);
    private static final Color TEXT_MUTED= new Color(107, 114, 128);
    private static final Color BORDER    = new Color(229, 231, 235);
    private static final Color BORDER_ROW= new Color(243, 244, 246);

    public static byte[] construir(List<Proveedor> proveedores) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            float PW = PageSize.A4.getWidth();
            float PAD = 32f;

            int rows = proveedores.size();
            float headerH = 75f, tblHdr = 20f, rowH = 22f, footH = 40f, gap = 12f;
            float totalH = headerH + gap + tblHdr + (rowH * rows) + gap + footH;
            float pageH = Math.max(totalH, 400f);

            Document doc = new Document(new com.lowagie.text.Rectangle(PW, pageH), 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
            BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
            PdfContentByte cb = writer.getDirectContent();

            drawRect(cb, 0, 0, PW, pageH, BG_PAGE);
            drawRect(cb, 0, pageH - headerH, PW, headerH, BG_WHITE);
            drawRect(cb, 0, pageH - 3, PW, 3, ORANGE);
            drawLine(cb, 0, pageH - headerH, PW, BORDER);

            cb.beginText();
            cb.setFontAndSize(bfBold, 22);
            cb.setColorFill(TEXT_DARK);
            cb.setTextMatrix(PAD, pageH - headerH + 38);
            cb.showText("Drive");
            cb.setColorFill(ORANGE);
            cb.showText("Master");
            cb.endText();

            cb.beginText();
            cb.setFontAndSize(bf, 7);
            cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix(PAD, pageH - headerH + 22);
            cb.showText("PROVEEDORES");
            cb.endText();

            String title = "REPORTE DE PROVEEDORES";
            float tw = bfBold.getWidthPoint(title, 10);
            cb.beginText(); cb.setFontAndSize(bfBold, 10); cb.setColorFill(ORANGE);
            cb.setTextMatrix(PW - PAD - tw, pageH - headerH + 42); cb.showText(title); cb.endText();

            String count = proveedores.size() + " registros";
            float cw = bf.getWidthPoint(count, 8);
            cb.beginText(); cb.setFontAndSize(bf, 8); cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix(PW - PAD - cw, pageH - headerH + 26); cb.showText(count); cb.endText();

            float tblY = pageH - headerH - gap;
            drawRect(cb, 0, tblY - tblHdr, PW, tblHdr, BG_STRIPE);
            drawLine(cb, 0, tblY, PW, BORDER);
            drawLine(cb, 0, tblY - tblHdr, PW, BORDER);

            String[] cols = {"NOMBRE", "NIT", "TEL\u00c9FONO", "CORREO"};
            float[] colW = {
                (PW - PAD * 2) * 0.30f, (PW - PAD * 2) * 0.20f,
                (PW - PAD * 2) * 0.20f, (PW - PAD * 2) * 0.30f
            };
            float x = PAD;
            for (int i = 0; i < cols.length; i++) {
                drawTableHeader(cb, bf, x, tblY - 13, cols[i], TEXT_MUTED);
                x += colW[i];
            }

            float rowY = tblY - tblHdr;
            boolean odd = true;
            for (Proveedor p : proveedores) {
                drawRect(cb, 0, rowY - rowH, PW, rowH, odd ? BG_WHITE : BG_STRIPE);
                drawLine(cb, 0, rowY - rowH, PW, BORDER_ROW);
                x = PAD;
                drawText(cb, bfBold, 8, safe(p.getNombre()), x + 4, rowY - 14, TEXT_DARK); x += colW[0];
                drawText(cb, bf, 8, safe(p.getNit()), x + 4, rowY - 14, TEXT_BODY); x += colW[1];
                drawText(cb, bf, 8, safe(p.getTelefono()), x + 4, rowY - 14, TEXT_BODY); x += colW[2];
                drawText(cb, bf, 8, safe(p.getCorreo()), x + 4, rowY - 14, TEXT_BODY);
                rowY -= rowH;
                odd = !odd;
            }
            drawLine(cb, 0, rowY, PW, BORDER);

            float footY = rowY - gap;
            drawRect(cb, 0, footY - footH, PW, footH, BG_STRIPE);
            drawLine(cb, 0, footY, PW, BORDER);
            String foot = "DriveMaster \u2014 Reporte generado autom\u00e1ticamente";
            float fw = bf.getWidthPoint(foot, 7.5f);
            cb.beginText(); cb.setFontAndSize(bf, 7.5f); cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix((PW - fw) / 2, footY - footH + 20); cb.showText(foot); cb.endText();

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de proveedores: " + e.getMessage(), e);
        }
    }

    private static void drawRect(PdfContentByte cb, float x, float y, float w, float h, Color c) {
        cb.setColorFill(c); cb.rectangle(x, y, w, h); cb.fill();
    }
    private static void drawLine(PdfContentByte cb, float x, float y, float w, Color c) {
        cb.setColorStroke(c); cb.setLineWidth(0.5f);
        cb.moveTo(x, y); cb.lineTo(x + w, y); cb.stroke();
    }
    private static void drawText(PdfContentByte cb, BaseFont bf, float size, String text, float x, float y, Color color) {
        cb.beginText(); cb.setFontAndSize(bf, size); cb.setColorFill(color);
        cb.setTextMatrix(x, y); cb.showText(safe(text)); cb.endText();
    }
    private static void drawTableHeader(PdfContentByte cb, BaseFont bf, float x, float y, String text, Color color) {
        cb.beginText(); cb.setFontAndSize(bf, 7); cb.setColorFill(color);
        cb.setTextMatrix(x, y); cb.showText(text); cb.endText();
    }
    private static String safe(String s) { return s != null && !s.isBlank() ? s : "\u2014"; }
}
