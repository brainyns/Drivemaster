package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfContentByte;

/** Logo de la marca en el encabezado de los PDF (sale de BRAND_LOGO_URL, ver {@link BrandConfig}). */
final class PdfMarca {

    private PdfMarca() {
    }

    /**
     * Dibuja el logo con su borde izquierdo en {@code x}, centrado en altura sobre {@code yCentro} y
     * escalado sin deformarse para caber en maxAncho × maxAlto. Devuelve false si no hay logo
     * (sin URL, descarga fallida o imagen inválida) para que quien llama escriba el nombre en texto.
     */
    static boolean dibujarLogo(PdfContentByte cb, float x, float yCentro, float maxAncho, float maxAlto) {
        byte[] bytes = BrandConfig.logoParaPdf();
        if (bytes == null) return false;
        try {
            Image logo = Image.getInstance(bytes);
            logo.scaleToFit(maxAncho, maxAlto);
            logo.setAbsolutePosition(x, yCentro - logo.getScaledHeight() / 2);
            cb.addImage(logo);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
