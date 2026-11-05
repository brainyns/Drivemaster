package com.drivemaster.drivemaster.util;

import static com.drivemaster.drivemaster.util.PlantillaCorreo.COLOR_LINEA;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.COLOR_TEXTO;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.COLOR_TEXTO_SUAVE;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.COLOR_TITULO;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.e;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.filaDato;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.filaTotal;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.moneda;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.rotulo;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;

/** Factura de venta por correo, sobre la plantilla común. Todo dato del usuario se escapa. */
public class EmailVentaBuilder {

    private static final String PIE = "Este documento es una confirmación electrónica de su compra en DriveMaster. "
            + "© 2026 DriveMaster · Todos los derechos reservados";

    public static String construir(Usuario usuario, Venta venta, double iva) {

        String fecha = venta.getFecha() != null
                ? venta.getFecha().format(
                        DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es")))
                : "—";

        String idCorto = venta.getId() != null
                ? venta.getId().substring(Math.max(0, venta.getId().length() - 8)).toUpperCase()
                : "—";

        double ivaValor = venta.getProductos().stream()
                .mapToDouble(d -> d.getSubtotal() != null ? d.getSubtotal() : 0)
                .sum() * iva;
        int ivaPct = (int) Math.round(iva * 100);

        String derecha = PlantillaCorreo.etiquetaHtml("Factura de venta")
                + "<br/><span style=\"display:inline-block;margin-top:6px;font-size:20px;font-weight:700;color:" + COLOR_TITULO + ";\">#"
                + e(idCorto) + "</span>";

        String cuerpo = datosCliente(usuario, fecha, venta.getEstado())
                + rotulo("Detalle de productos")
                + tablaProductos(venta, ivaPct)
                + pagosYTotales(venta, ivaValor);

        return PlantillaCorreo.documento("Factura DriveMaster #" + idCorto, derecha, null, cuerpo, PIE);
    }

    private static String datosCliente(Usuario usuario, String fecha, String estado) {
        String identificacion = usuario.getIdentificacion() != null && !usuario.getIdentificacion().isBlank()
                ? "<p style=\"margin:0;font-size:12px;color:" + COLOR_TEXTO_SUAVE + ";\">CC: " + e(usuario.getIdentificacion()) + "</p>"
                : "";
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;\"><tr>"
                + "<td class=\"dm-apilar\" width=\"50%\" valign=\"top\" style=\"vertical-align:top;\">"
                + rotulo("Cliente")
                + "<p style=\"margin:0 0 2px;font-size:15px;font-weight:600;color:" + COLOR_TITULO + ";\">" + e(usuario.getNombre()) + "</p>"
                + "<p style=\"margin:0 0 2px;font-size:13px;color:" + COLOR_TEXTO_SUAVE + ";\">" + e(usuario.getCorreo()) + "</p>"
                + identificacion
                + "</td>"
                + "<td class=\"dm-apilar\" width=\"50%\" align=\"right\" valign=\"top\" style=\"vertical-align:top;text-align:right;\">"
                + rotulo("Fecha de emisión")
                + "<p style=\"margin:0 0 8px;font-size:13px;color:" + COLOR_TEXTO + ";\">" + e(fecha) + "</p>"
                + "<span style=\"display:inline-block;padding:4px 12px;background:" + BrandConfig.colorPrimarioSuave()
                + ";color:" + COLOR_TITULO + ";font-size:11px;font-weight:700;border-radius:20px;letter-spacing:1px;\">"
                + e(estado) + "</span>"
                + "</td></tr></table>";
    }

    private static String tablaProductos(Venta venta, int ivaPct) {
        StringBuilder filas = new StringBuilder();
        for (DetalleVenta d : venta.getProductos()) {
            double conIva = (d.getPrecioUnitario() != null ? d.getPrecioUnitario() : 0) * (1 + ivaPct / 100.0);
            double linea = conIva * (d.getCantidad() != null ? d.getCantidad() : 0);
            filas.append("<tr>")
                    .append(celda(e(d.getNombre()), "left", ""))
                    .append(celda(String.valueOf(d.getCantidad()), "center", ""))
                    .append(celda(moneda(conIva), "right", "white-space:nowrap;"))
                    .append(celda(moneda(linea), "right", "white-space:nowrap;font-weight:600;"))
                    .append("</tr>");
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;border-collapse:collapse;\">"
                + "<tr>" + titulo("Producto", "left") + titulo("Cant.", "center") + titulo("Precio unit. (IVA incluido)", "right") + titulo("Subtotal (IVA incluido)", "right") + "</tr>"
                + filas + "</table>";
    }

    private static String titulo(String texto, String alineacion) {
        return "<th class=\"dm-celda\" align=\"" + alineacion + "\" style=\"padding:8px;text-align:" + alineacion + ";font-size:11px;color:"
                + COLOR_TEXTO_SUAVE + ";text-transform:uppercase;letter-spacing:1px;font-weight:600;border-bottom:2px solid " + COLOR_LINEA + ";\">"
                + e(texto) + "</th>";
    }

    private static String celda(String html, String alineacion, String extra) {
        return "<td class=\"dm-celda\" align=\"" + alineacion + "\" style=\"padding:10px 8px;border-bottom:1px solid " + COLOR_LINEA
                + ";color:" + COLOR_TEXTO + ";font-size:13px;text-align:" + alineacion + ";" + extra + "\">" + html + "</td>";
    }

    private static String pagosYTotales(Venta venta, double ivaValor) {
        StringBuilder pagos = new StringBuilder();
        for (Pago p : venta.getPagos()) {
            pagos.append(filaDato(p.getMetodo(), moneda(p.getMonto())));
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td class=\"dm-apilar\" width=\"50%\" valign=\"top\" style=\"vertical-align:top;padding-right:16px;\">"
                + rotulo("Forma de pago")
                + PlantillaCorreo.tabla(pagos.toString())
                + "</td>"
                + "<td class=\"dm-apilar\" width=\"50%\" valign=\"top\" style=\"vertical-align:top;\">"
                + rotulo("Resumen")
                + PlantillaCorreo.tabla(filaTotal("Total", moneda(venta.getTotal()))
                        + filaDato("Incluye IVA de", moneda(ivaValor)))
                + "</td></tr></table>";
    }
}
