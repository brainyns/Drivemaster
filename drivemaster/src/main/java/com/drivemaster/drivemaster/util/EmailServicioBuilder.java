package com.drivemaster.drivemaster.util;

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

    // ─── Taller ──────────────────────────────────────────────────────────────

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

    private static String e(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto);
    }

    private static String unir(String a, String b) {
        if (a == null || a.isBlank()) return b != null ? b : "";
        if (b == null || b.isBlank()) return a;
        return a + ", " + b;
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
