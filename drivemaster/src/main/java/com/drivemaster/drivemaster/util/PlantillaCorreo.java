package com.drivemaster.drivemaster.util;

import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.web.util.HtmlUtils;

import com.drivemaster.drivemaster.config.BrandConfig;

/**
 * Diseño común de todos los correos: fondo blanco, encabezado con el logo (y el nombre), franja
 * naranja debajo, cuerpo en gris oscuro y pie. Tablas y estilos en línea, que es lo que respetan Gmail
 * y los clientes de celular. Colores y logo vienen de {@link BrandConfig}.
 */
public final class PlantillaCorreo {

    public static final String PIE_POR_DEFECTO =
            "Este es un mensaje automático de DriveMaster. Puedes ver el avance de tus servicios en tu panel.";

    /** Títulos, totales y datos destacados. */
    public static final String COLOR_TITULO = "#1F2937";
    /** Texto del cuerpo. */
    public static final String COLOR_TEXTO = "#374151";
    /** Etiquetas y notas: gris legible sobre blanco. */
    public static final String COLOR_TEXTO_SUAVE = "#6B7280";
    public static final String COLOR_LINEA = "#E5E7EB";

    private static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");

    private PlantillaCorreo() {
    }

    /** Correo completo. {@code cuerpo} ya es HTML; etiqueta, título y pie se escapan. */
    public static String documento(String etiqueta, String titulo, String cuerpo, String pie) {
        return documento(etiqueta, etiquetaHtml(etiqueta), titulo, cuerpo, pie);
    }

    /**
     * Correo con lo que va a la derecha del logo ya armado ({@code derechaHtml}). Sin {@code titulo}
     * no lleva encabezado de cuerpo. {@code cuerpo} ya es HTML; tituloPagina, título y pie se escapan.
     */
    public static String documento(String tituloPagina, String derechaHtml, String titulo, String cuerpo, String pie) {
        String encabezadoCuerpo = titulo == null ? ""
                : "<h1 style=\"margin:0 0 16px;font-size:20px;line-height:1.3;color:" + COLOR_TITULO + ";\">" + e(titulo) + "</h1>";
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <meta name="color-scheme" content="light"/>
                  <meta name="supported-color-schemes" content="light"/>
                  <title>%s</title>
                  %s
                </head>
                <body class="body dm-fondo" style="margin:0;padding:0;background-color:#ffffff;font-family:'Segoe UI',Arial,sans-serif;">
                  <table class="dm-fondo" role="presentation" width="100%%" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="background-color:#ffffff;">
                    <tr><td class="dm-externa" align="center" style="padding:24px 12px;">
                      <table class="dm-fondo" role="presentation" width="620" cellpadding="0" cellspacing="0" bgcolor="#ffffff"
                             style="max-width:620px;width:100%%;background-color:#ffffff;border-radius:8px;border:1px solid %s;">
                        %s
                        <tr>
                          <td class="dm-cuerpo dm-fondo" bgcolor="#ffffff" style="padding:32px 36px;background-color:#ffffff;font-size:14px;line-height:1.6;color:%s;">
                            %s
                          </td>
                        </tr>
                        <tr>
                          <td class="dm-pie dm-fondo" bgcolor="#ffffff" style="padding:18px 36px;background-color:#ffffff;border-top:1px solid %s;">
                            %s
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(e(tituloPagina), ESTILOS, COLOR_LINEA, encabezado(derechaHtml), COLOR_TEXTO,
                capaGmail(encabezadoCuerpo + cuerpo), COLOR_LINEA,
                capaGmail("<p style=\"margin:0;font-size:12px;color:" + COLOR_TEXTO_SUAVE + ";line-height:1.6;\">"
                        + e(pie != null ? pie : PIE_POR_DEFECTO) + "</p>"));
    }

    /**
     * Ajustes para pantallas angostas (va en el head de cada correo): la etiqueta baja debajo de la
     * marca, los márgenes se reducen y las columnas marcadas con dm-apilar quedan una debajo de otra.
     * color-scheme pide a los clientes que respetan la preferencia no invertir los colores.
     */
    private static final String ESTILOS_MOVIL = """
            <style>
              :root { color-scheme: light; supported-color-schemes: light; }
              @media only screen and (max-width:480px) {
                .dm-externa { padding:0 !important; }
                .dm-encabezado { padding:18px 20px 14px !important; }
                .dm-cuerpo { padding:24px 20px !important; }
                .dm-pie { padding:16px 20px !important; }
                .dm-col { display:block !important; width:100% !important; text-align:left !important; }
                .dm-col-etiqueta { padding-top:12px !important; }
                .dm-logo-img { max-width:110px !important; }
                .dm-nombre { font-size:19px !important; }
                .dm-apilar { display:block !important; width:100% !important; padding:0 0 16px !important; text-align:left !important; box-sizing:border-box; }
                .dm-celda { padding-left:4px !important; padding-right:4px !important; }
              }
            </style>""";

    /**
     * Modo claro en Gmail, que ignora color-scheme e invierte fondos y textos; u + .body solo coincide allí.
     * El degradado conserva el blanco porque Gmail cambia background-color pero no background-image. Va aquí y
     * no en línea para que, donde se borra este estilo (Gmail con cuentas que no son de Google), no quede fondo
     * blanco con el texto ya aclarado. La capa difference deja los colores igual sin inversión (|0 - C| = C) y
     * los reinvierte con ella, porque Gmail vuelve blanco su fondo negro (|1 - C|). El truco conocido con
     * screen solo sirve para texto blanco: sobre fondo blanco borraría el correo.
     */
    private static final String ESTILOS_GMAIL = """
            <style>
              u + .body, u + .body .dm-fondo { background-image:linear-gradient(#ffffff,#ffffff) !important; }
              u + .body .dm-gmail-fondo { background:#000000; }
              u + .body .dm-gmail-capa { background:#ffffff; mix-blend-mode:difference; }
              u + .body .dm-gmail-recto { border-radius:0 !important; }
            </style>""";

    /**
     * Modo oscuro de Outlook.com, que marca lo que recolorea con data-ogsc (texto) y data-ogsb (fondo). Va en un
     * bloque aparte para no poner en riesgo las reglas de Gmail, que no admite selectores de atributo.
     */
    private static final String ESTILOS_OUTLOOK = """
            <style>
              [data-ogsb] .dm-fondo { background-color:#ffffff !important; }
              [data-ogsc] .dm-fondo h1, [data-ogsc] .dm-fondo p, [data-ogsc] .dm-fondo td,
              [data-ogsc] .dm-fondo th, [data-ogsc] .dm-fondo span, [data-ogsc] .dm-fondo a { color:%s !important; }
            </style>""".formatted(COLOR_TITULO);

    /** Todo lo que va en el head de cada correo. */
    public static final String ESTILOS = ESTILOS_MOVIL + ESTILOS_GMAIL + ESTILOS_OUTLOOK;

    /** Texto oscuro sobre blanco que debe seguir legible en Gmail. Fuera de Gmail los div no hacen nada. */
    private static String capaGmail(String html) {
        return "<div class=\"dm-gmail-fondo\"><div class=\"dm-gmail-capa\">" + html + "</div></div>";
    }

    /**
     * Dentro de {@link #capaGmail}: una segunda inversión evita que el naranja de marca salga azul. Lo envuelto
     * lleva la clase dm-gmail-recto, porque en las esquinas redondeadas se vería la capa negra de abajo.
     */
    private static String conservarColor(String html) {
        String estilo = " style=\"display:inline-block;\">";
        return "<span class=\"dm-gmail-fondo\"" + estilo + "<span class=\"dm-gmail-capa\"" + estilo + html + "</span></span>";
    }

    /**
     * Filas del encabezado: zona blanca con la marca (logo y nombre) a la izquierda y
     * {@code derechaHtml} a la derecha, y debajo la franja del color principal (nunca detrás del logo).
     */
    public static String encabezado(String derechaHtml) {
        return """
                <tr>
                  <td class="dm-encabezado dm-fondo" bgcolor="#ffffff" style="background-color:#ffffff;padding:22px 36px 18px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr>
                      <td class="dm-col" valign="middle" style="vertical-align:middle;">%s</td>
                      <td class="dm-col dm-col-etiqueta" align="right" valign="middle" style="vertical-align:middle;">%s</td>
                    </tr></table>
                  </td>
                </tr>
                <tr>
                  <td bgcolor="%1$s" style="background-color:%1$s;background-image:linear-gradient(%1$s,%1$s);height:4px;line-height:4px;font-size:0;">&nbsp;</td>
                </tr>
                """.replace("%1$s", BrandConfig.colorPrimario())
                .formatted(marca(), capaGmail(derechaHtml != null ? derechaHtml : ""));
    }

    /** Logo (si hay URL) y a su derecha el nombre, centrados en altura. */
    public static String marca() {
        String url = BrandConfig.logoUrl();
        String logo = url.isEmpty() ? ""
                : "<td valign=\"middle\" style=\"vertical-align:middle;padding-right:12px;\">" + logo() + "</td>";
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>" + logo
                + "<td valign=\"middle\" style=\"vertical-align:middle;\">" + capaGmail(nombre()) + "</td></tr></table>";
    }

    /** Logo como imagen remota de URL pública, con texto alternativo (vacío si no hay URL). */
    public static String logo() {
        String url = BrandConfig.logoUrl();
        if (url.isEmpty()) return "";
        return "<img class=\"dm-logo-img\" src=\"" + HtmlUtils.htmlEscape(url) + "\" alt=\"Logo de " + e(BrandConfig.nombre())
                + "\" width=\"" + BrandConfig.logoAncho() + "\" style=\"display:block;border:0;height:auto;\">";
    }

    /** Nombre como en la página: "DriveMaster" en oscuro con el punto naranja. */
    public static String nombre() {
        return "<span class=\"dm-nombre\" style=\"font-family:'Segoe UI',Arial,sans-serif;font-size:24px;font-weight:800;"
                + "letter-spacing:-0.5px;white-space:nowrap;color:" + BrandConfig.colorTexto() + ";\">"
                + e(BrandConfig.nombre()) + "<span style=\"color:" + BrandConfig.colorPrimario() + ";\">.</span></span>";
    }

    /** Etiqueta del encabezado: píldora del color principal. */
    public static String etiquetaHtml(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return conservarColor("<span class=\"dm-gmail-recto\" style=\"display:inline-block;padding:5px 12px;background:" + BrandConfig.colorPrimario()
                + ";color:" + BrandConfig.colorSobrePrimario() + ";font-size:11px;font-weight:700;letter-spacing:1.5px;"
                + "text-transform:uppercase;border-radius:20px;\">" + e(texto) + "</span>");
    }

    /** Botón principal: fondo naranja con texto oscuro, igual que en la web. */
    public static String boton(String texto, String url) {
        return "<span style=\"display:inline-block;margin:0 8px 8px 0;\">" + conservarColor(
                "<a class=\"dm-gmail-recto\" href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;padding:11px 18px;"
                + "background:" + BrandConfig.colorPrimario() + ";color:" + BrandConfig.colorSobrePrimario()
                + ";text-decoration:none;border-radius:6px;font-size:14px;font-weight:700;\">" + e(texto) + "</a>") + "</span>";
    }

    /** Botón secundario: contorno gris sobre blanco. */
    public static String botonContorno(String texto, String url) {
        return "<a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;margin:0 8px 8px 0;padding:10px 17px;"
                + "background:#ffffff;color:" + COLOR_TEXTO + ";border:1px solid #d1d5db;text-decoration:none;border-radius:6px;"
                + "font-size:14px;font-weight:600;\">" + e(texto) + "</a>";
    }

    /** Recuadro destacado: tinte claro y borde izquierdo del color principal. */
    public static String recuadro(String html) {
        return "<div style=\"margin:0 0 16px;padding:14px 16px;background:" + BrandConfig.colorPrimarioSuave()
                + ";border-left:4px solid " + BrandConfig.colorPrimario()
                + ";border-radius:4px;font-size:14px;line-height:1.6;color:" + COLOR_TEXTO + ";\">" + html + "</div>";
    }

    public static String parrafo(String html) {
        return "<p style=\"margin:0 0 12px;font-size:14px;line-height:1.6;color:" + COLOR_TEXTO + ";\">" + html + "</p>";
    }

    /** Nota al pie de un bloque, más pequeña. */
    public static String nota(String html) {
        return "<span style=\"font-size:12px;color:" + COLOR_TEXTO_SUAVE + ";\">" + html + "</span>";
    }

    /** Monto destacado (total a pagar o pagado). */
    public static String montoDestacado(String monto) {
        return "<p style=\"margin:0 0 16px;font-size:24px;font-weight:700;color:" + COLOR_TITULO + ";\">" + e(monto) + "</p>";
    }

    /** Rótulo de una sección del cuerpo (Cliente, Forma de pago...). */
    public static String rotulo(String texto) {
        return "<p style=\"margin:0 0 6px;font-size:11px;color:" + COLOR_TEXTO_SUAVE
                + ";text-transform:uppercase;letter-spacing:1.5px;\">" + e(texto) + "</p>";
    }

    /** Tabla de ancho completo para filas de datos; {@code filas} ya es HTML. */
    public static String tabla(String filas) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px;\">"
                + filas + "</table>";
    }

    /** Fila con detalle a la izquierda y valor a la derecha, separada de la siguiente; ambos ya son HTML. */
    public static String filaDetalle(String izquierdaHtml, String derechaHtml) {
        String celda = "padding:8px 0;border-bottom:1px solid " + COLOR_LINEA + ";font-size:13px;color:" + COLOR_TEXTO + ";";
        return "<tr><td style=\"" + celda + "\">" + izquierdaHtml + "</td>"
                + "<td align=\"right\" style=\"" + celda + "white-space:nowrap;padding-left:12px;\">" + derechaHtml + "</td></tr>";
    }

    /** Concepto y valor de un desglose. {@code concepto} se escapa; {@code valorHtml} ya es HTML. */
    public static String filaDato(String concepto, String valorHtml) {
        return "<tr><td style=\"padding:4px 0;font-size:13px;color:" + COLOR_TEXTO_SUAVE + ";\">" + e(concepto) + "</td>"
                + "<td align=\"right\" style=\"padding:4px 0 4px 12px;font-size:13px;color:" + COLOR_TEXTO + ";\">" + valorHtml + "</td></tr>";
    }

    /** Fila del total: línea del color principal encima y el monto en gris oscuro. */
    public static String filaTotal(String concepto, String monto) {
        String borde = "border-top:2px solid " + BrandConfig.colorPrimario() + ";";
        String texto = "font-size:15px;font-weight:700;color:" + COLOR_TITULO + ";";
        return "<tr><td style=\"padding:10px 0 0;" + borde + texto + "\">" + e(concepto) + "</td>"
                + "<td align=\"right\" style=\"padding:10px 0 0 12px;" + borde + texto + "\">" + monto + "</td></tr>";
    }

    /** Pesos colombianos sin decimales. */
    public static String moneda(Double valor) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
        fmt.setMaximumFractionDigits(0);
        return fmt.format(valor != null ? valor.doubleValue() : 0);
    }

    public static String e(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto);
    }
}
