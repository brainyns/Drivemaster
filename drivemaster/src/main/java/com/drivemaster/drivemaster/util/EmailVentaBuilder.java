package com.drivemaster.drivemaster.util;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Venta;

public class EmailVentaBuilder {

    private static final Locale LOCALE_CO = new Locale("es", "CO");

    public static String construir(Usuario usuario, Venta venta, double iva) {

        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
        fmt.setMaximumFractionDigits(0);

        String fecha = venta.getFecha() != null
                ? venta.getFecha().format(
                        DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy, HH:mm", new Locale("es")))
                : "—";

        String idCorto = venta.getId() != null
                ? venta.getId().substring(Math.max(0, venta.getId().length() - 8)).toUpperCase()
                : "—";

        double subtotalBruto = venta.getProductos().stream()
                .mapToDouble(d -> d.getSubtotal() != null ? d.getSubtotal() : 0)
                .sum();
        double ivaValor = subtotalBruto * iva;

        // ── Filas de productos ────────────────────────
        StringBuilder filasProductos = new StringBuilder();
        for (DetalleVenta d : venta.getProductos()) {
            filasProductos.append("""
                    <tr>
                      <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#374151;font-size:13px;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#374151;font-size:13px;text-align:center;">%d</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#374151;font-size:13px;text-align:right;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#111827;font-size:13px;text-align:right;font-weight:600;">%s</td>
                    </tr>
                    """.formatted(
                    d.getNombre(),
                    d.getCantidad(),
                    fmt.format(d.getPrecioUnitario()),
                    fmt.format(d.getSubtotal())
            ));
        }

        // ── Filas de pagos ────────────────────────────
        StringBuilder filasPagos = new StringBuilder();
        for (Pago p : venta.getPagos()) {
            filasPagos.append("""
                    <tr>
                      <td style="padding:6px 0;color:#6b7280;font-size:13px;">%s</td>
                      <td style="padding:6px 0;color:#111827;font-size:13px;text-align:right;">%s</td>
                    </tr>
                    """.formatted(p.getMetodo(), fmt.format(p.getMonto())));
        }

        // ── Identificación del usuario (opcional) ─────
        String identificacionCliente = (usuario.getIdentificacion() != null
                && !usuario.getIdentificacion().isBlank())
                        ? "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">CC: "
                                + usuario.getIdentificacion() + "</p>"
                        : "";

        int ivaPct = (int) Math.round(iva * 100);

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <title>Factura DriveMaster #%s</title>
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
                                  Factura de Venta
                                </span><br/>
                                <span style="font-size:20px;font-weight:700;color:#111827;">#%s</span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>

                        <!-- ── META CLIENTE / FECHA ── -->
                        <tr>
                          <td style="padding:24px 36px;border-bottom:1px solid #e5e7eb;background:#f9fafb;">
                            <table width="100%%" cellpadding="0" cellspacing="0"><tr>
                              <td style="width:50%%;vertical-align:top;">
                                <p style="margin:0 0 4px;font-size:10px;color:#9ca3af;text-transform:uppercase;letter-spacing:1.5px;">Cliente</p>
                                <p style="margin:0 0 2px;font-size:15px;color:#111827;font-weight:600;">%s</p>
                                <p style="margin:0 0 2px;font-size:12px;color:#6b7280;">%s</p>
                                %s
                              </td>
                              <td style="width:50%%;vertical-align:top;text-align:right;">
                                <p style="margin:0 0 4px;font-size:10px;color:#9ca3af;text-transform:uppercase;letter-spacing:1.5px;">Fecha de emisión</p>
                                <p style="margin:0 0 8px;font-size:13px;color:#374151;">%s</p>
                                <span style="display:inline-block;padding:4px 12px;background:#d1fae5;color:#065f46;
                                             font-size:11px;font-weight:700;border-radius:20px;letter-spacing:1px;">
                                  %s
                                </span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>

                        <!-- ── TABLA PRODUCTOS ── -->
                        <tr>
                          <td style="padding:24px 36px 0;">
                            <p style="margin:0 0 12px;font-size:10px;color:#9ca3af;text-transform:uppercase;letter-spacing:1.5px;">
                              Detalle de productos
                            </p>
                            <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">
                              <thead>
                                <tr style="background:#f3f4f6;">
                                  <th style="padding:10px 12px;text-align:left;font-size:10px;color:#6b7280;text-transform:uppercase;letter-spacing:1px;font-weight:600;border-bottom:1px solid #e5e7eb;">Producto</th>
                                  <th style="padding:10px 12px;text-align:center;font-size:10px;color:#6b7280;text-transform:uppercase;letter-spacing:1px;font-weight:600;border-bottom:1px solid #e5e7eb;">Cant.</th>
                                  <th style="padding:10px 12px;text-align:right;font-size:10px;color:#6b7280;text-transform:uppercase;letter-spacing:1px;font-weight:600;border-bottom:1px solid #e5e7eb;">Precio unit.</th>
                                  <th style="padding:10px 12px;text-align:right;font-size:10px;color:#6b7280;text-transform:uppercase;letter-spacing:1px;font-weight:600;border-bottom:1px solid #e5e7eb;">Subtotal</th>
                                </tr>
                              </thead>
                              <tbody>%s</tbody>
                            </table>
                          </td>
                        </tr>

                        <!-- ── TOTALES + PAGOS ── -->
                        <tr>
                          <td style="padding:20px 36px 24px;">
                            <table width="100%%" cellpadding="0" cellspacing="0"><tr>

                              <!-- Forma de pago -->
                              <td style="width:50%%;vertical-align:top;padding-right:16px;">
                                <p style="margin:0 0 10px;font-size:10px;color:#9ca3af;text-transform:uppercase;letter-spacing:1.5px;">Forma de pago</p>
                                <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
                              </td>

                              <!-- Resumen totales -->
                              <td style="width:50%%;vertical-align:top;">
                                <table width="100%%" cellpadding="0" cellspacing="0"
                                       style="background:#f9fafb;border-radius:6px;padding:16px;border:1px solid #e5e7eb;border-left:3px solid #E8450A;">
                                  <tr>
                                    <td style="padding:4px 0;color:#6b7280;font-size:12px;">Subtotal</td>
                                    <td style="padding:4px 0;color:#374151;font-size:12px;text-align:right;">%s</td>
                                  </tr>
                                  <tr>
                                    <td style="padding:4px 0;color:#6b7280;font-size:12px;">IVA (%d%%)</td>
                                    <td style="padding:4px 0;color:#374151;font-size:12px;text-align:right;">%s</td>
                                  </tr>
                                  <tr>
                                    <td style="padding:10px 0 4px;border-top:1px solid #e5e7eb;color:#111827;font-size:15px;font-weight:700;">Total</td>
                                    <td style="padding:10px 0 4px;border-top:1px solid #e5e7eb;color:#E8450A;font-size:15px;font-weight:700;text-align:right;">%s</td>
                                  </tr>
                                </table>
                              </td>

                            </tr></table>
                          </td>
                        </tr>

                        <!-- ── FOOTER ── -->
                        <tr>
                          <td style="background:#f9fafb;padding:20px 36px;border-top:1px solid #e5e7eb;text-align:center;">
                            <p style="margin:0 0 4px;font-size:12px;color:#6b7280;">
                              Este documento es una confirmación electrónica de su compra en DriveMaster.
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
                // <title>
                idCorto,
                // header #ID
                idCorto,
                // usuario nombre, correo, identificacion
                usuario.getNombre(),
                usuario.getCorreo(),
                identificacionCliente,
                // fecha, estado
                fecha,
                venta.getEstado(),
                // filas productos
                filasProductos,
                // filas pagos
                filasPagos,
                // subtotal, iva%, ivaValor, total
                fmt.format(subtotalBruto),
                ivaPct,
                fmt.format(ivaValor),
                fmt.format(venta.getTotal())
        );
    }
}