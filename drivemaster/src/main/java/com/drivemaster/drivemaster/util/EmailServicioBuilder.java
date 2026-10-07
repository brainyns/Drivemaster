package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Correos del flujo de solicitudes de servicio (marketplace). Mismo diseño que
 * los correos de ventas y solicitudes existentes. Todo dato que viene del usuario
 * se escapa antes de insertarse en el HTML.
 */
public final class EmailServicioBuilder {

    private static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", LOCALE_CO);

    private EmailServicioBuilder() {
    }

    // ─── Cliente ─────────────────────────────────────────────────────────────

    public static String clienteTallerAsignado(String cliente, String vehiculo, String taller,
                                               String direccionTaller, String ciudad) {
        return plantilla("Solicitud asignada", "Ya tienes taller asignado",
                saludo(cliente)
                + parrafo("Asignamos tu solicitud de diagnóstico para el <strong>" + e(vehiculo)
                        + "</strong> al taller más cercano:")
                + recuadro("<strong>" + e(taller) + "</strong><br/>"
                        + e(unir(direccionTaller, ciudad)))
                + parrafo("El taller revisará tu solicitud y te propondrá una fecha para la revisión."
                        + " Te avisaremos por este medio."));
    }

    public static String clienteSinTaller(String cliente, String vehiculo) {
        return plantilla("Sin taller disponible", "No encontramos taller disponible",
                saludo(cliente)
                + parrafo("Por ahora no hay talleres disponibles cerca de tu ubicación para atender la"
                        + " solicitud del <strong>" + e(vehiculo) + "</strong>.")
                + parrafo("Puedes intentarlo de nuevo más tarde o indicar otra ciudad o ubicación."));
    }

    public static String clienteCitaRevision(String cliente, String vehiculo, String taller, String direccion,
                                             LocalDateTime fecha, String mecanico, double tarifa,
                                             boolean pagoRequerido, LocalDateTime limitePago) {
        String detalle = "<strong>" + e(taller) + "</strong><br/>" + e(direccion) + "<br/>"
                + "Fecha: <strong>" + e(formatoFecha(fecha)) + "</strong>"
                + (mecanico != null ? "<br/>Mecánico: " + e(mecanico) : "")
                + "<br/>Tarifa de diagnóstico: <strong>" + moneda(tarifa) + "</strong>";
        String cierre = pagoRequerido
                ? parrafo("Para confirmar la cita, paga la tarifa de diagnóstico antes del <strong>"
                        + e(formatoFecha(limitePago)) + "</strong> desde tu panel en DriveMaster."
                        + " Si aceptas la reparación, este valor se descuenta de la cotización.")
                : parrafo("Si aceptas la reparación, la tarifa de diagnóstico se descuenta de la cotización.");
        return plantilla("Cita de revisión", pagoRequerido ? "Confirma tu cita de revisión" : "Tu cita de revisión está agendada",
                saludo(cliente)
                + parrafo("El taller aceptó tu solicitud para el <strong>" + e(vehiculo) + "</strong>.")
                + recuadro(detalle)
                + cierre);
    }

    public static String clienteSolicitudCancelada(String cliente, String vehiculo, String motivo) {
        return plantilla("Solicitud cancelada", "Tu solicitud fue cancelada",
                saludo(cliente)
                + parrafo("La solicitud de diagnóstico para el <strong>" + e(vehiculo) + "</strong> quedó cancelada.")
                + (motivo != null ? recuadro("Motivo: " + e(motivo)) : ""));
    }

    /**
     * Cotización para el cliente con el desglose y los botones de respuesta. Los
     * enlaces llevan el token en el fragmento (#token=…), que el navegador no envía
     * al servidor: no queda en logs de Vercel ni de Render.
     */
    public static String clienteCotizacion(String cliente, Cotizacion c, String enlaceBase) {
        StringBuilder filas = new StringBuilder();
        for (CotizacionItem i : c.getItems()) {
            String detalle = CotizacionItem.REPUESTO.equals(i.getTipo())
                    ? e(i.getProductoNombre()) + " · " + e(i.getTiendaNombre())
                        + ("ENCARGO".equals(i.getDisponibilidad()) && i.getDiasEntrega() != null
                            ? " · encargo " + i.getDiasEntrega() + " días" : "")
                    : (CotizacionItem.POR_HORAS.equals(i.getModalidad()) ? i.getHoras() + " h × " + moneda(i.getPrecioUnitario()) : "Mano de obra");
            String cantidad = CotizacionItem.REPUESTO.equals(i.getTipo()) ? i.getCantidad() + " × " : "";
            filas.append("<tr><td style=\"padding:8px 0;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;\">")
                    .append(cantidad).append(e(i.getDescripcion()))
                    .append("<br/><span style=\"color:#9ca3af;font-size:12px;\">").append(detalle).append("</span></td>")
                    .append("<td align=\"right\" style=\"padding:8px 0;border-bottom:1px solid #e5e7eb;font-size:13px;color:#0C2240;white-space:nowrap;\">")
                    .append(moneda(i.getSubtotal())).append("</td></tr>");
        }
        String totales = linea("Subtotal", c.getSubtotal())
                + linea("IVA (" + formatoPct(c.getIvaPct()) + ")", c.getIva())
                + linea("Total", c.getTotal())
                + (c.getAnticipoDiagnostico() != null && c.getAnticipoDiagnostico() > 0
                    ? linea("Diagnóstico ya pagado", -c.getAnticipoDiagnostico()) : "")
                + "<tr><td style=\"padding:10px 0 0;font-size:15px;font-weight:700;color:#0C2240;\">Total a pagar</td>"
                + "<td align=\"right\" style=\"padding:10px 0 0;font-size:15px;font-weight:700;color:#0C2240;\">"
                + moneda(c.getTotalAPagar()) + "</td></tr>";

        String extras = (c.getTiempoEstimadoHoras() != null ? "Tiempo estimado del trabajo: " + c.getTiempoEstimadoHoras() + " h<br/>" : "")
                + (c.getDiasEntregaRepuestos() != null ? "Entrega de repuestos por encargo: hasta " + c.getDiasEntregaRepuestos() + " días<br/>" : "")
                + "Válida hasta el <strong>" + e(formatoFecha(c.getVigenciaHasta())) + "</strong>"
                + (c.getObservaciones() != null && !c.getObservaciones().isBlank() ? "<br/>Nota del taller: " + e(c.getObservaciones()) : "");

        return plantilla("Cotización", "Tu cotización está lista",
                saludo(cliente)
                + parrafo("<strong>" + e(c.getTallerNombre()) + "</strong> preparó la cotización para tu <strong>"
                        + e(c.getVehiculoDescripcion()) + "</strong>. Revísala y decide.")
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px;\">" + filas
                + totales + "</table>"
                + recuadro(extras)
                + "<p style=\"margin:20px 0 8px;\">"
                + boton("Revisar y aprobar", enlaceBase + "&accion=aprobar", "#E8450A")
                + boton("Pedir cambios", enlaceBase + "&accion=cambios", "#18487A")
                + boton("Rechazar", enlaceBase + "&accion=rechazar", "#6b7280")
                + "</p>"
                + parrafo("<span style=\"font-size:12px;color:#9ca3af;\">El enlace es personal y sirve una sola vez. "
                        + "Para pagar te pediremos iniciar sesión.</span>"));
    }

    // ─── Taller ──────────────────────────────────────────────────────────────

    public static String tallerRespuestaCotizacion(String taller, String vehiculo, int version, String respuesta, String motivo) {
        return plantilla("Respuesta del cliente", "El cliente respondió tu cotización",
                saludo(taller)
                + parrafo("Cotización v" + version + " del <strong>" + e(vehiculo) + "</strong>: <strong>" + e(respuesta) + "</strong>.")
                + (motivo != null && !motivo.isBlank() ? recuadro("Comentario del cliente: " + e(motivo)) : "")
                + parrafo("Revisa la solicitud en tu panel."));
    }

    public static String tallerCotizacionVencida(String taller, String vehiculo, int version) {
        return plantilla("Cotización vencida", "Una cotización venció sin respuesta",
                saludo(taller)
                + parrafo("La cotización v" + version + " del <strong>" + e(vehiculo) + "</strong> venció sin respuesta del cliente. "
                        + "La solicitud volvió a \"Diagnosticada\": puedes emitir una nueva cotización con precios actualizados."));
    }

    public static String tallerNuevaSolicitud(String taller, String vehiculo, String sintomas, String zona,
                                              String preferencia, LocalDateTime limiteRespuesta) {
        return plantilla("Nueva solicitud", "Tienes una nueva solicitud de diagnóstico",
                saludo(taller)
                + parrafo("Te asignamos una solicitud por cercanía:")
                + recuadro("<strong>" + e(vehiculo) + "</strong><br/>"
                        + "Síntomas: " + e(sintomas) + "<br/>"
                        + "Zona: " + e(zona)
                        + (preferencia != null && !preferencia.isBlank() ? "<br/>Preferencia de horario: " + e(preferencia) : ""))
                + parrafo("Acéptala o recházala desde tu panel antes del <strong>" + e(formatoFecha(limiteRespuesta))
                        + "</strong>. Si no respondes a tiempo, la solicitud pasa al siguiente taller."));
    }

    public static String tallerSolicitudRetirada(String taller, String vehiculo, String motivo) {
        return plantilla("Solicitud retirada", "Una solicitud salió de tu bandeja",
                saludo(taller)
                + parrafo("La solicitud de diagnóstico del <strong>" + e(vehiculo) + "</strong> ya no está asignada a tu taller.")
                + (motivo != null ? recuadro("Motivo: " + e(motivo)) : ""));
    }

    // ─── Plantilla ───────────────────────────────────────────────────────────

    private static String plantilla(String etiqueta, String titulo, String cuerpo) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:40px 0;">
                    <tr><td align="center">
                      <table width="620" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;box-shadow:0 4px 16px rgba(0,0,0,0.07);">
                        <tr>
                          <td style="background:#0C2240;padding:28px 36px;border-bottom:3px solid #E8450A;">
                            <table width="100%%" cellpadding="0" cellspacing="0"><tr>
                              <td>
                                <span style="font-size:26px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;">
                                  Drive<span style="color:#E8450A;">Master</span>
                                </span><br/>
                                <span style="font-size:11px;color:#9fb3c8;letter-spacing:2px;text-transform:uppercase;">
                                  Talleres y autopartes
                                </span>
                              </td>
                              <td align="right">
                                <span style="font-size:11px;color:#E8450A;letter-spacing:2px;text-transform:uppercase;font-weight:700;">
                                  %s
                                </span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px 36px;">
                            <h1 style="margin:0 0 16px;font-size:20px;color:#0C2240;">%s</h1>
                            %s
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 36px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;font-size:11px;color:#9ca3af;line-height:1.6;">
                              Este es un mensaje automático de DriveMaster. Puedes ver el avance de tus servicios en tu panel.
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(e(etiqueta), e(etiqueta), e(titulo), cuerpo);
    }

    private static String saludo(String nombre) {
        return parrafo("Hola <strong>" + e(nombre != null ? nombre : "") + "</strong>,");
    }

    private static String parrafo(String html) {
        return "<p style=\"margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;\">" + html + "</p>";
    }

    private static String recuadro(String html) {
        return "<div style=\"margin:0 0 16px;padding:14px 16px;background:#f3f6fa;border-left:4px solid #18487A;"
                + "border-radius:4px;font-size:14px;line-height:1.6;color:#0C2240;\">" + html + "</div>";
    }

    private static String linea(String concepto, Double valor) {
        return "<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">" + e(concepto) + "</td>"
                + "<td align=\"right\" style=\"padding:4px 0;font-size:13px;color:#374151;\">" + moneda(valor) + "</td></tr>";
    }

    private static String boton(String texto, String url, String color) {
        return "<a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;margin:0 8px 8px 0;padding:11px 18px;"
                + "background:" + color + ";color:#ffffff;text-decoration:none;border-radius:6px;font-size:14px;font-weight:600;\">"
                + e(texto) + "</a>";
    }

    private static String formatoPct(Double pct) {
        if (pct == null) return "";
        return (pct == Math.floor(pct) ? String.valueOf(pct.intValue()) : String.valueOf(pct)) + " %";
    }

    private static String e(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto);
    }

    private static String unir(String a, String b) {
        if (a == null || a.isBlank()) return b != null ? b : "";
        if (b == null || b.isBlank()) return a;
        return a + ", " + b;
    }

    private static String moneda(Double valor) {
        return moneda(valor != null ? valor.doubleValue() : 0);
    }

    private static String moneda(double valor) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
        fmt.setMaximumFractionDigits(0);
        return fmt.format(valor);
    }

    private static String formatoFecha(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FECHA_HORA) : "";
    }
}
