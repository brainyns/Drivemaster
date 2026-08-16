package com.drivemaster.drivemaster.util;

import java.text.NumberFormat;
import java.util.Locale;

import com.drivemaster.drivemaster.model.Usuario;

public class EmailSolicitudBuilder {

    private static final Locale LOCALE_CO = new Locale("es", "CO");

    public static String pagoRecibido(Usuario usuario, double total) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
        fmt.setMaximumFractionDigits(0);

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <title>Pago recibido</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:'Segoe UI',Arial,sans-serif;">

                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:40px 0;">
                    <tr><td align="center">
                      <table width="620" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;box-shadow:0 4px 16px rgba(0,0,0,0.07);">

                        <!-- ── HEADER ── -->
                        <tr>
                          <td style="background:#ffffff;padding:28px 36px;border-bottom:3px solid #E8450A;">
                            <table width="100%%" cellpadding="0" cellspacing="0"><tr>
                              <td>
                                <span style="font-size:26px;font-weight:800;color:#111827;letter-spacing:-0.5px;">
                                  Drive<span style="color:#E8450A;">Master</span>
                                </span><br/>
                                <span style="font-size:11px;color:#9ca3af;letter-spacing:2px;text-transform:uppercase;">
                                  Repuestos Automotrices
                                </span>
                              </td>
                              <td align="right">
                                <span style="font-size:11px;color:#E8450A;letter-spacing:2px;text-transform:uppercase;font-weight:700;">
                                  Pago recibido
                                </span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>

                        <!-- ── CUERPO ── -->
                        <tr>
                          <td style="padding:32px 36px;">
                            <h1 style="margin:0 0 16px;font-size:20px;color:#111827;">Hemos recibido tu pago</h1>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;">
                              Hola <strong>%s</strong>,
                            </p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;">
                              Gracias por tu compra. Hemos recibido tu pago por:
                            </p>
                            <p style="margin:0 0 16px;font-size:24px;font-weight:700;color:#E8450A;">%s</p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;">
                              Tu pedido está siendo gestionado y será enviado en un plazo de
                              <strong>7 a 15 días hábiles</strong>. Te notificaremos por correo cuando esté en camino.
                            </p>
                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6b7280;">
                              Si tienes dudas, contáctanos por WhatsApp al <strong>573015335263</strong>.
                            </p>
                          </td>
                        </tr>

                        <!-- ── FOOTER ── -->
                        <tr>
                          <td style="background:#f9fafb;padding:20px 36px;border-top:1px solid #e5e7eb;text-align:center;">
                            <p style="margin:0 0 4px;font-size:12px;color:#6b7280;">
                              Gracias por confiar en DriveMaster.
                            </p>
                            <p style="margin:0;font-size:11px;color:#9ca3af;">
                              © 2026 DriveMaster · Todos los derechos reservados
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>

                </body>
                </html>
                """.formatted(
                usuario.getNombre() != null ? usuario.getNombre() : "cliente",
                fmt.format(total)
        );
    }

    public static String entregado(Usuario usuario) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <title>Pedido entregado</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:'Segoe UI',Arial,sans-serif;">

                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:40px 0;">
                    <tr><td align="center">
                      <table width="620" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;box-shadow:0 4px 16px rgba(0,0,0,0.07);">

                        <!-- ── HEADER ── -->
                        <tr>
                          <td style="background:#ffffff;padding:28px 36px;border-bottom:3px solid #E8450A;">
                            <table width="100%%" cellpadding="0" cellspacing="0"><tr>
                              <td>
                                <span style="font-size:26px;font-weight:800;color:#111827;letter-spacing:-0.5px;">
                                  Drive<span style="color:#E8450A;">Master</span>
                                </span><br/>
                                <span style="font-size:11px;color:#9ca3af;letter-spacing:2px;text-transform:uppercase;">
                                  Repuestos Automotrices
                                </span>
                              </td>
                              <td align="right">
                                <span style="font-size:11px;color:#E8450A;letter-spacing:2px;text-transform:uppercase;font-weight:700;">
                                  Pedido entregado
                                </span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>

                        <!-- ── CUERPO ── -->
                        <tr>
                          <td style="padding:32px 36px;">
                            <h1 style="margin:0 0 16px;font-size:20px;color:#111827;">¡Tu pedido ha sido entregado!</h1>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;">
                              Hola <strong>%s</strong>,
                            </p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#374151;">
                              Tu pedido ha sido entregado. Gracias por comprar con DriveMaster, esperamos que disfrutes tu compra.
                            </p>
                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6b7280;">
                              Si tienes dudas, contáctanos por WhatsApp al <strong>573015335263</strong>.
                            </p>
                          </td>
                        </tr>

                        <!-- ── FOOTER ── -->
                        <tr>
                          <td style="background:#f9fafb;padding:20px 36px;border-top:1px solid #e5e7eb;text-align:center;">
                            <p style="margin:0 0 4px;font-size:12px;color:#6b7280;">
                              Gracias por confiar en DriveMaster.
                            </p>
                            <p style="margin:0;font-size:11px;color:#9ca3af;">
                              © 2026 DriveMaster · Todos los derechos reservados
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>

                </body>
                </html>
                """.formatted(usuario.getNombre() != null ? usuario.getNombre() : "cliente");
    }
}
