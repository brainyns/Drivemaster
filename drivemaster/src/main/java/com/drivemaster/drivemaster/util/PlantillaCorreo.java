package com.drivemaster.drivemaster.util;

import org.springframework.web.util.HtmlUtils;

import com.drivemaster.drivemaster.config.BrandConfig;

/**
 * Diseño común de todos los correos: encabezado blanco con el logo (o el nombre si no hay
 * BRAND_LOGO_URL), franja del color principal debajo, cuerpo y pie. Colores y logo vienen de
 * {@link BrandConfig}.
 */
public final class PlantillaCorreo {

    public static final String PIE_POR_DEFECTO =
            "Este es un mensaje automático de DriveMaster. Puedes ver el avance de tus servicios en tu panel.";

    private PlantillaCorreo() {
    }

    /** Correo completo. {@code cuerpo} ya es HTML; etiqueta, título y pie se escapan. */
    public static String documento(String etiqueta, String titulo, String cuerpo, String pie) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:'Segoe UI',Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:40px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="620" cellpadding="0" cellspacing="0"
                             style="max-width:620px;width:100%%;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;box-shadow:0 4px 16px rgba(0,0,0,0.07);">
                        %s
                        <tr>
                          <td style="padding:32px 36px;font-size:14px;line-height:1.6;color:#374151;">
                            <h1 style="margin:0 0 16px;font-size:20px;color:#111827;">%s</h1>
                            %s
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 36px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;font-size:11px;color:#9ca3af;line-height:1.6;">%s</p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(e(etiqueta), encabezado(etiquetaHtml(etiqueta)), e(titulo), cuerpo,
                e(pie != null ? pie : PIE_POR_DEFECTO));
    }

    /**
     * Filas del encabezado: zona blanca con el logo a la izquierda y {@code derechaHtml} a la
     * derecha, y debajo la franja del color principal (nunca detrás del logo, que tiene fondo claro).
     */
    public static String encabezado(String derechaHtml) {
        return """
                <tr>
                  <td style="background:#ffffff;padding:22px 36px 18px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr>
                      <td valign="middle">%s</td>
                      <td align="right" valign="middle">%s</td>
                    </tr></table>
                  </td>
                </tr>
                <tr>
                  <td style="background:%s;height:4px;line-height:4px;font-size:0;">&nbsp;</td>
                </tr>
                """.formatted(logo(), derechaHtml != null ? derechaHtml : "", BrandConfig.colorPrimario());
    }

    /** Logo como imagen remota; si BRAND_LOGO_URL está vacía, el nombre de la marca en texto. */
    public static String logo() {
        String url = BrandConfig.logoUrl();
        if (url.isEmpty()) {
            return "<span style=\"font-size:26px;font-weight:800;color:#111827;letter-spacing:-0.5px;\">"
                    + e(BrandConfig.nombre()) + "</span>";
        }
        return "<img src=\"" + HtmlUtils.htmlEscape(url) + "\" alt=\"" + e(BrandConfig.nombre())
                + "\" width=\"160\" style=\"display:block;border:0;height:auto;\">";
    }

    /** Etiqueta del encabezado: píldora del color principal. */
    public static String etiquetaHtml(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return "<span style=\"display:inline-block;padding:5px 12px;background:" + BrandConfig.colorPrimario()
                + ";color:" + BrandConfig.colorSobrePrimario() + ";font-size:11px;font-weight:700;letter-spacing:1.5px;"
                + "text-transform:uppercase;border-radius:20px;\">" + e(texto) + "</span>";
    }

    /** Botón principal: fondo del color principal. */
    public static String boton(String texto, String url) {
        return "<a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;margin:0 8px 8px 0;padding:11px 18px;"
                + "background:" + BrandConfig.colorPrimario() + ";color:" + BrandConfig.colorSobrePrimario()
                + ";text-decoration:none;border-radius:6px;font-size:14px;font-weight:700;\">" + e(texto) + "</a>";
    }

    /** Botón secundario: contorno gris sobre blanco. */
    public static String botonContorno(String texto, String url) {
        return "<a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;margin:0 8px 8px 0;padding:10px 17px;"
                + "background:#ffffff;color:#374151;border:1px solid #d1d5db;text-decoration:none;border-radius:6px;"
                + "font-size:14px;font-weight:600;\">" + e(texto) + "</a>";
    }

    /** Recuadro destacado: tinte claro y borde izquierdo del color principal. */
    public static String recuadro(String html) {
        return "<div style=\"margin:0 0 16px;padding:14px 16px;background:" + BrandConfig.colorPrimarioSuave()
                + ";border-left:4px solid " + BrandConfig.colorPrimario()
                + ";border-radius:4px;font-size:14px;line-height:1.6;color:#111827;\">" + html + "</div>";
    }

    public static String parrafo(String html) {
        return "<p style=\"margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;\">" + html + "</p>";
    }

    /** Monto destacado (total a pagar o pagado). */
    public static String montoDestacado(String monto) {
        return "<p style=\"margin:0 0 16px;font-size:24px;font-weight:700;color:" + BrandConfig.colorPrimarioTexto()
                + ";\">" + e(monto) + "</p>";
    }

    public static String e(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto);
    }
}
