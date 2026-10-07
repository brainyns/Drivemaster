package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PedidoTienda;
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

    /**
     * Comprobante de pago (no es una factura: cada taller y tienda factura lo suyo).
     * lineas: concepto → valor; puede ir vacía.
     */
    public static String clienteComprobantePago(String cliente, String concepto, String vehiculo, String referencia,
                                                String transaccion, LocalDateTime fecha, double monto,
                                                java.util.Map<String, Double> lineas, String siguientePaso) {
        StringBuilder detalle = new StringBuilder();
        if (lineas != null) lineas.forEach((k, v) -> detalle.append(linea(k, v)));
        return plantilla("Comprobante de pago", "Comprobante de pago",
                saludo(cliente)
                + parrafo("Recibimos tu pago de <strong>" + e(concepto) + "</strong> para tu <strong>" + e(vehiculo) + "</strong>.")
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px;\">"
                + detalle
                + "<tr><td style=\"padding:10px 0 0;font-size:15px;font-weight:700;color:#0C2240;\">Total pagado</td>"
                + "<td align=\"right\" style=\"padding:10px 0 0;font-size:15px;font-weight:700;color:#0C2240;\">"
                + moneda(monto) + "</td></tr></table>"
                + recuadro("Referencia: <strong>" + e(referencia) + "</strong><br/>"
                        + (transaccion != null ? "Transacción Wompi: " + e(transaccion) + "<br/>" : "")
                        + "Fecha: " + e(formatoFecha(fecha)))
                + (siguientePaso != null ? parrafo(e(siguientePaso)) : "")
                + parrafo("<span style=\"font-size:12px;color:#9ca3af;\">Este comprobante no es una factura. "
                        + "El taller y las tiendas expiden la suya por sus productos y servicios.</span>"));
    }

    /** El pago llegó pero no se pudo aplicar: se devolverá el dinero. */
    public static String clientePagoNoAplicado(String cliente, String vehiculo, double monto, String motivo) {
        return plantilla("Reembolso", "Te devolveremos tu pago",
                saludo(cliente)
                + parrafo("Recibimos un pago de <strong>" + moneda(monto) + "</strong> para tu <strong>" + e(vehiculo)
                        + "</strong>, pero no se pudo aplicar.")
                + recuadro("Motivo: " + e(motivo))
                + parrafo("DriveMaster gestionará el reembolso al mismo medio de pago. Te avisaremos cuando esté hecho."));
    }

    public static String clientePedidoNoDespachado(String cliente, String vehiculo, String tienda, double monto) {
        return plantilla("Repuestos", "Una tienda no pudo despachar",
                saludo(cliente)
                + parrafo("<strong>" + e(tienda) + "</strong> no pudo despachar los repuestos para tu <strong>"
                        + e(vehiculo) + "</strong>.")
                + recuadro("Te devolveremos <strong>" + moneda(monto) + "</strong> (lo que pagaste por esos repuestos). "
                        + "El taller te contactará para conseguirlos por otro medio.")
                + parrafo("Puedes ver el avance en tu panel."));
    }

    public static String clienteVehiculoListo(String cliente, String vehiculo, String taller, String direccion) {
        return plantilla("Vehículo listo", "Tu vehículo está listo",
                saludo(cliente)
                + parrafo("<strong>" + e(taller) + "</strong> terminó el trabajo de tu <strong>" + e(vehiculo)
                        + "</strong>. Ya puedes recogerlo.")
                + (direccion != null ? recuadro("Dirección: " + e(direccion)) : "")
                + parrafo("Cuando lo recibas, confírmalo en \"Mis servicios\" y, si quieres, califica al taller."));
    }

    // ─── Tienda ──────────────────────────────────────────────────────────────

    public static String tiendaNuevoPedido(String tienda, PedidoTienda p) {
        StringBuilder filas = new StringBuilder();
        for (CotizacionItem i : p.getItems()) {
            filas.append("<tr><td style=\"padding:6px 0;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;\">")
                    .append(i.getCantidad()).append(" × ").append(e(i.getProductoNombre()))
                    .append(i.getReferencia() != null ? " <span style=\"color:#9ca3af;\">(" + e(i.getReferencia()) + ")</span>" : "")
                    .append("ENCARGO".equals(i.getDisponibilidad()) ? " · encargo" : "")
                    .append("</td><td align=\"right\" style=\"padding:6px 0;border-bottom:1px solid #e5e7eb;font-size:13px;\">")
                    .append(moneda(i.getSubtotal())).append("</td></tr>");
        }
        return plantilla("Nuevo pedido", "Tienes un pedido pagado",
                saludo(tienda)
                + parrafo("El cliente ya pagó. Lleva estos repuestos al taller:")
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px;\">" + filas
                + linea("IVA", p.getIva()) + linea("Total del pedido", p.getTotal()) + "</table>"
                + recuadro("Entregar en: <strong>" + e(p.getTallerNombre()) + "</strong><br/>"
                        + e(unir(p.getTallerDireccion(), p.getTallerCiudad()))
                        + (p.getTallerTelefono() != null ? "<br/>Tel. " + e(p.getTallerTelefono()) : "")
                        + "<br/>Vehículo: " + e(p.getVehiculoDescripcion()))
                + (p.getPlazoDespachoHasta() != null
                        ? parrafo("<strong>Plazo máximo para despachar: " + e(FECHA_HORA.format(p.getPlazoDespachoHasta())) + "</strong>")
                        : "")
                + parrafo("Marca el pedido como despachado en tu panel, o \"No puedo despachar\" si no lo puedes surtir."));
    }

    // ─── Compras directas del catálogo ───────────────────────────────────────

    /**
     * Comprobante de una compra del catálogo. Lleva el código de retiro en claro (es la única
     * vez que sale del sistema junto con el botón "Ver código" de Mis compras; en la base solo
     * queda su hash). codigos: pedidoId → código, solo para los pedidos con retiro.
     */
    public static String clienteCompraPagada(String cliente, com.drivemaster.drivemaster.model.CompraCatalogo compra,
                                             java.util.List<PedidoTienda> pedidos, java.util.Map<String, String> codigos,
                                             String referencia, String transaccion, LocalDateTime fecha) {
        StringBuilder detalle = new StringBuilder();
        for (PedidoTienda p : pedidos) {
            detalle.append("<p style=\"margin:14px 0 4px;font-size:14px;font-weight:700;color:#0C2240;\">")
                    .append(e(p.getTiendaNombre())).append(" · ").append(moneda(p.getTotal())).append("</p>");
            if (p.getItems() != null) {
                for (CotizacionItem i : p.getItems()) {
                    detalle.append("<p style=\"margin:0;font-size:13px;color:#374151;\">")
                            .append(i.getCantidad()).append(" × ").append(e(i.getProductoNombre()))
                            .append("ENCARGO".equals(i.getDisponibilidad()) ? " · por encargo" : "").append("</p>");
                }
            }
            if (PedidoTienda.RETIRO.equals(p.getEntrega())) {
                String codigo = codigos != null ? codigos.get(p.getId()) : null;
                detalle.append(recuadro("<strong>Retiro en la tienda</strong><br/>"
                        + e(unir(p.getTiendaDireccion(), p.getTiendaCiudad())) + "<br/>Horario: " + e(p.getTiendaHorario())
                        + (codigo != null ? "<br/>Código de retiro: <strong style=\"font-size:18px;letter-spacing:2px;\">"
                        + e(codigo) + "</strong>" : "")
                        + "<br/>Te avisaremos cuando esté listo para retirar."));
            } else {
                detalle.append(recuadro("<strong>Envío a domicilio</strong> (" + moneda(p.getCostoEnvio()) + ")<br/>"
                        + e(unir(p.getEnvioDireccion(), p.getEnvioCiudad()))));
            }
        }
        return plantilla("Compra", "Comprobante de tu compra",
                saludo(cliente)
                + parrafo("Recibimos tu pago y avisamos a cada tienda. Esto es lo que compraste:")
                + detalle
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 16px;\">"
                + linea("Repuestos", compra.getSubtotal()) + linea("IVA", compra.getIva())
                + linea("Envío", compra.getCostoEnvio())
                + "<tr><td style=\"padding:10px 0 0;font-size:15px;font-weight:700;color:#0C2240;\">Total pagado</td>"
                + "<td align=\"right\" style=\"padding:10px 0 0;font-size:15px;font-weight:700;color:#0C2240;\">"
                + moneda(compra.getTotal()) + "</td></tr></table>"
                + recuadro("Referencia: <strong>" + e(referencia) + "</strong><br/>"
                        + (transaccion != null ? "Transacción Wompi: " + e(transaccion) + "<br/>" : "")
                        + "Fecha: " + e(formatoFecha(fecha)))
                + parrafo("Guarda el código de retiro: la tienda lo pedirá al entregarte el pedido. "
                        + "Si lo pierdes, puedes generar uno nuevo desde Mis compras.")
                + parrafo("<span style=\"font-size:12px;color:#9ca3af;\">Este comprobante no es una factura. "
                        + "Cada tienda expide la suya por sus productos.</span>"));
    }

    /** Pedido nuevo de una compra directa: la tienda ve QUÉ entregar y CÓMO (retiro o envío), no el código. */
    public static String tiendaNuevaCompra(String tienda, PedidoTienda p) {
        StringBuilder filas = new StringBuilder();
        for (CotizacionItem i : p.getItems()) {
            filas.append("<tr><td style=\"padding:6px 0;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;\">")
                    .append(i.getCantidad()).append(" × ").append(e(i.getProductoNombre()))
                    .append(i.getReferencia() != null ? " <span style=\"color:#9ca3af;\">(" + e(i.getReferencia()) + ")</span>" : "")
                    .append("ENCARGO".equals(i.getDisponibilidad()) ? " · encargo" : "")
                    .append("</td><td align=\"right\" style=\"padding:6px 0;border-bottom:1px solid #e5e7eb;font-size:13px;\">")
                    .append(moneda(i.getSubtotal())).append("</td></tr>");
        }
        boolean envio = PedidoTienda.ENVIO.equals(p.getEntrega());
        return plantilla("Nuevo pedido", "Tienes una compra pagada",
                saludo(tienda)
                + parrafo("Un cliente compró en tu tienda y ya pagó:")
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 16px;\">" + filas
                + linea("IVA", p.getIva())
                + (envio ? linea("Envío", p.getCostoEnvio()) : "")
                + linea("Total del pedido", p.getTotal()) + "</table>"
                + recuadro(envio
                        ? "<strong>Enviar a</strong> " + e(p.getEnvioReceptor()) + "<br/>"
                          + e(unir(p.getEnvioDireccion(), p.getEnvioCiudad()))
                          + "<br/>Tel. " + e(p.getEnvioTelefono())
                          + (p.getEnvioIndicaciones() != null ? "<br/>" + e(p.getEnvioIndicaciones()) : "")
                        : "<strong>Retiro en tu tienda</strong> por " + e(p.getClienteNombre())
                          + ". Cuando el pedido esté listo márcalo como \"Listo para retirar\"; "
                          + "al entregarlo, pide al cliente su código de retiro y valídalo en tu panel.")
                + (p.getPlazoDespachoHasta() != null
                        ? parrafo("<strong>Plazo máximo: " + e(FECHA_HORA.format(p.getPlazoDespachoHasta())) + "</strong>")
                        : ""));
    }

    public static String clientePedidoListoParaRetirar(String cliente, PedidoTienda p) {
        return plantilla("Retiro", "Tu pedido está listo para retirar",
                saludo(cliente)
                + parrafo("<strong>" + e(p.getTiendaNombre()) + "</strong> ya tiene listo tu pedido.")
                + recuadro("Retíralo en: <strong>" + e(unir(p.getTiendaDireccion(), p.getTiendaCiudad())) + "</strong><br/>"
                        + "Horario: " + e(p.getTiendaHorario()))
                + parrafo("Lleva tu <strong>código de retiro</strong> (el del correo de tu compra o uno nuevo desde Mis compras): "
                        + "la tienda lo pedirá al entregarte el pedido y solo sirve una vez."));
    }

    public static String clienteEnvioEnCamino(String cliente, PedidoTienda p, int diasAutoconfirmacion) {
        return plantilla("Envío", "Tu pedido va en camino",
                saludo(cliente)
                + parrafo("<strong>" + e(p.getTiendaNombre()) + "</strong> despachó tu pedido a "
                        + e(unir(p.getEnvioDireccion(), p.getEnvioCiudad())) + ".")
                + (p.getNotaDespacho() != null ? recuadro("Información del envío: " + e(p.getNotaDespacho())) : "")
                + parrafo("Cuando lo recibas, entra a <strong>Mis compras</strong> y pulsa <strong>\"Ya lo recibí\"</strong>. "
                        + "Si no respondes en " + diasAutoconfirmacion + " días hábiles lo damos por recibido.")
                + parrafo("¿No llegó o llegó con un problema? Pulsa <strong>\"Reportar un problema\"</strong> en Mis compras "
                        + "y pausamos el pago a la tienda mientras lo revisamos."));
    }

    public static String tiendaProblemaEnvio(String tienda, PedidoTienda p, String detalle) {
        return plantilla("Envío", "El cliente reportó un problema",
                saludo(tienda)
                + parrafo("El cliente reportó un problema con el pedido que enviaste a "
                        + e(unir(p.getEnvioDireccion(), p.getEnvioCiudad())) + ":")
                + recuadro(e(detalle))
                + parrafo("DriveMaster lo está revisando. Mientras tanto el pago de este pedido queda en pausa."));
    }

    public static String clienteProblemaEnvioResuelto(String cliente, String tienda, boolean reembolsa, double monto) {
        return plantilla("Envío", "Resolvimos el problema con tu pedido",
                saludo(cliente)
                + parrafo(reembolsa
                        ? "Revisamos el problema con el pedido de <strong>" + e(tienda) + "</strong> y te devolveremos <strong>"
                          + moneda(monto) + "</strong> al mismo medio de pago. Te avisaremos cuando esté hecho."
                        : "Revisamos el problema con el pedido de <strong>" + e(tienda) + "</strong> y quedó registrado como entregado."));
    }

    public static String clienteCompraNoAplicada(String cliente, double monto, String motivo) {
        return plantilla("Reembolso", "Te devolveremos tu pago",
                saludo(cliente)
                + parrafo("Recibimos tu pago de <strong>" + moneda(monto) + "</strong>, pero no pudimos completar tu compra.")
                + recuadro("Motivo: " + e(motivo))
                + parrafo("No se envió ningún pedido. DriveMaster gestionará el reembolso completo al mismo medio de pago "
                        + "y te avisaremos cuando esté hecho."));
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────

    public static String adminAlerta(String titulo, String detalle) {
        return plantilla("Alerta", titulo,
                parrafo(e(detalle))
                + parrafo("Revisa Liquidaciones y reembolsos en el panel de administración."));
    }

    // ─── Taller ──────────────────────────────────────────────────────────────

    public static String tallerDiagnosticoPagado(String taller, String vehiculo, LocalDateTime cita) {
        return plantilla("Diagnóstico pagado", "El cliente pagó el diagnóstico",
                saludo(taller)
                + parrafo("La revisión del <strong>" + e(vehiculo) + "</strong> quedó confirmada para el <strong>"
                        + e(formatoFecha(cita)) + "</strong>."));
    }

    public static String tallerCotizacionPagada(String taller, String vehiculo, int pedidos) {
        return plantilla("Cotización pagada", "El cliente pagó la cotización",
                saludo(taller)
                + parrafo("El cliente pagó la cotización del <strong>" + e(vehiculo) + "</strong>.")
                + parrafo(pedidos > 0
                        ? "Pedimos los repuestos a " + pedidos + " tienda(s). Confirma en tu panel cuando los recibas."
                        : "La cotización es solo mano de obra: puedes empezar el trabajo."));
    }

    public static String tallerPedidoNoDespachado(String taller, String vehiculo, String tienda, String motivo) {
        return plantilla("Repuestos", "Una tienda no puede despachar",
                saludo(taller)
                + parrafo("<strong>" + e(tienda) + "</strong> no puede despachar los repuestos del <strong>"
                        + e(vehiculo) + "</strong>.")
                + (motivo != null && !motivo.isBlank() ? recuadro("Motivo: " + e(motivo)) : "")
                + parrafo("El cliente recibirá el reembolso de esos repuestos. Coordina con él cómo conseguirlos."));
    }

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
