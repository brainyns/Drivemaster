package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;

/** Marca en el encabezado de los PDF: logo (de BRAND_LOGO_URL, ver {@link BrandConfig}) y nombre. */
final class PdfMarca {

    private PdfMarca() {
    }

    /**
     * Dibuja el logo con su borde izquierdo en {@code x}, centrado en altura sobre {@code yCentro} y
     * escalado sin deformarse para caber en maxAncho × maxAlto. Devuelve el ancho dibujado, o 0 si no
     * hay logo (sin URL, descarga fallida o imagen inválida).
     */
    static float dibujarLogo(PdfContentByte cb, float x, float yCentro, float maxAncho, float maxAlto) {
        byte[] bytes = BrandConfig.logoParaPdf();
        if (bytes == null) return 0f;
        try {
            Image logo = Image.getInstance(bytes);
            logo.scaleToFit(maxAncho, maxAlto);
            logo.setAbsolutePosition(x, yCentro - logo.getScaledHeight() / 2);
            cb.addImage(logo);
            return logo.getScaledWidth();
        } catch (Exception e) {
            return 0f;
        }
    }

    /** Nombre como en la página: "DriveMaster" en oscuro con el punto naranja, con la base en (x, y). */
    static void dibujarNombre(PdfContentByte cb, BaseFont bfBold, float tamano, float x, float y) {
        cb.beginText();
        cb.setFontAndSize(bfBold, tamano);
        cb.setColorFill(BrandConfig.colorTextoAwt());
        cb.setTextMatrix(x, y);
        cb.showText(BrandConfig.nombre());
        cb.setColorFill(BrandConfig.colorPrimarioAwt());
        cb.showText(".");
        cb.endText();
    }
}
