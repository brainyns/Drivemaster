package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.Notificacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.EmailServicioBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Avisos del flujo de solicitudes: por correo y en la campanita (guardados y en tiempo real).
 * Un fallo de envío nunca revierte el cambio de estado: se registra en el log y el flujo sigue.
 */
@Service
public class AvisosSolicitudServicio {

    private static final Logger log = LoggerFactory.getLogger(AvisosSolicitudServicio.class);

    private final EmailsService emailsService;
    private final UsuarioRepository usuarioRepository;
    private final TallerRepository tallerRepository;
    private final TiendaRepository tiendaRepository;
    private final NotificacionService notificaciones;
    private final String frontendUrl;
    // SOLO desarrollo: con MAIL_DEV_LOG activo y llaves de prueba de Wompi (pub_test_) el código de retiro se
    // escribe en el log del backend. Nunca en pantalla ni en la API.
    @Value("${mail.dev-log:false}")
    private boolean devLog;
    @Value("${wompi.public_key:}")
    private String wompiPublicKey;

    private boolean registrarCodigosEnLog() {
        return devLog && wompiPublicKey != null && wompiPublicKey.startsWith("pub_test_");
    }

    public AvisosSolicitudServicio(EmailsService emailsService,
                                   UsuarioRepository usuarioRepository,
                                   TallerRepository tallerRepository,
                                   TiendaRepository tiendaRepository,
                                   NotificacionService notificaciones,
                                   @Value("${frontend.url}") String frontendUrl) {
        this.tiendaRepository = tiendaRepository;
        this.notificaciones = notificaciones;
        this.emailsService = emailsService;
        this.usuarioRepository = usuarioRepository;
        this.tallerRepository = tallerRepository;
        this.frontendUrl = frontendUrl;
    }

    public void tallerAsignado(OrdenServicio orden, Taller taller) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Asignamos tu solicitud a " + taller.getNombre(),
                EmailServicioBuilder.clienteTallerAsignado(c.getNombre(), orden.getVehiculoDescripcion(),
                        taller.getNombre(), taller.getDireccion(), taller.getCiudad())));
        correoTaller(taller).ifPresent(correo -> enviar(correo, "Nueva solicitud de diagnóstico",
                EmailServicioBuilder.tallerNuevaSolicitud(taller.getNombre(), orden.getVehiculoDescripcion(),
                        orden.getSintomas(), zona(orden), orden.getPreferenciaHorario(),
                        orden.getFechaLimiteRespuesta())));
        avisar(orden.getUsuarioId(), Notificacion.SOLICITUD_ASIGNADA, "Asignamos tu solicitud",
                taller.getNombre() + " atenderá " + vehiculo(orden) + ".", "mis-servicios", orden.getId());
        avisar(taller.getUsuarioId(), Notificacion.SOLICITUD_NUEVA, "Nueva solicitud de diagnóstico",
                vehiculo(orden) + " en " + zona(orden) + ".", "taller-solicitudes", orden.getId());
    }

    public void sinTallerDisponible(OrdenServicio orden) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "No encontramos taller disponible",
                EmailServicioBuilder.clienteSinTaller(c.getNombre(), orden.getVehiculoDescripcion())));
        avisar(orden.getUsuarioId(), Notificacion.SIN_TALLER, "No encontramos taller disponible",
                "Ningún taller pudo tomar la solicitud de " + vehiculo(orden) + ".", "mis-servicios", orden.getId());
    }

    public void citaRevision(OrdenServicio orden, boolean pagoRequerido) {
        Taller taller = orden.getTallerId() != null ? tallerRepository.findById(orden.getTallerId()).orElse(null) : null;
        if (taller == null) return;
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(),
                pagoRequerido ? "Confirma tu cita de revisión" : "Tu cita de revisión está agendada",
                EmailServicioBuilder.clienteCitaRevision(c.getNombre(), orden.getVehiculoDescripcion(),
                        taller.getNombre(), taller.getDireccion(), orden.getFechaProgramada(),
                        orden.getMecanicoNombre(),
                        orden.getTarifaDiagnostico() != null ? orden.getTarifaDiagnostico() : 0,
                        pagoRequerido, orden.getFechaLimitePagoDiagnostico())));
        avisar(orden.getUsuarioId(), Notificacion.CITA_REVISION,
                pagoRequerido ? "Confirma tu cita de revisión" : "Tu cita de revisión está agendada",
                "Revisión de " + vehiculo(orden) + " en " + taller.getNombre()
                        + (pagoRequerido ? ". Paga el diagnóstico para confirmarla." : "."),
                "mis-servicios", orden.getId());
    }

    public void solicitudCancelada(OrdenServicio orden, String motivo) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Tu solicitud fue cancelada",
                EmailServicioBuilder.clienteSolicitudCancelada(c.getNombre(), orden.getVehiculoDescripcion(), motivo)));
        avisar(orden.getUsuarioId(), Notificacion.SOLICITUD_CANCELADA, "Tu solicitud fue cancelada",
                "La solicitud de " + vehiculo(orden) + " se canceló" + motivo(motivo), "mis-servicios", orden.getId());
    }

    /** Aviso al taller que pierde una solicitud (reasignada o cancelada). */
    public void solicitudRetirada(String tallerId, OrdenServicio orden, String motivo) {
        if (tallerId == null) return;
        tallerRepository.findById(tallerId).ifPresent(taller -> {
            correoTaller(taller).ifPresent(correo -> enviar(correo, "Una solicitud salió de tu bandeja",
                    EmailServicioBuilder.tallerSolicitudRetirada(taller.getNombre(),
                            orden.getVehiculoDescripcion(), motivo)));
            avisar(taller.getUsuarioId(), Notificacion.SOLICITUD_RETIRADA, "Una solicitud salió de tu bandeja",
                    vehiculo(orden) + motivo(motivo), "taller-solicitudes", orden.getId());
        });
    }

    /**
     * Cotización emitida. El token va en el fragmento del enlace (nunca llega a un
     * servidor) y no se escribe en el log, ni siquiera si el envío falla.
     */
    public void cotizacionEmitida(OrdenServicio orden, Cotizacion cotizacion, String token) {
        String enlace = frontendUrl.replaceAll("/+$", "") + "/cotizacion#token=" + token;
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(),
                "Tu cotización de " + cotizacion.getTallerNombre() + " está lista",
                EmailServicioBuilder.clienteCotizacion(c.getNombre(), cotizacion, enlace)));
        avisar(orden.getUsuarioId(), Notificacion.COTIZACION_EMITIDA, "Tu cotización está lista",
                cotizacion.getTallerNombre() + " envió la cotización de " + vehiculo(orden) + ".",
                "mis-servicios", orden.getId());
    }

    public void cotizacionRespondida(Cotizacion cotizacion, String respuesta, String motivo) {
        tallerRepository.findById(cotizacion.getTallerId()).ifPresent(taller -> {
            correoTaller(taller).ifPresent(correo -> enviar(correo, "Respuesta a tu cotización: " + respuesta,
                    EmailServicioBuilder.tallerRespuestaCotizacion(taller.getNombre(),
                            cotizacion.getVehiculoDescripcion(), cotizacion.getVersion(), respuesta, motivo)));
            avisar(taller.getUsuarioId(), Notificacion.COTIZACION_RESPONDIDA, "Cotización " + respuesta,
                    "Cotización v" + cotizacion.getVersion() + " de " + vehiculo(cotizacion.getVehiculoDescripcion())
                            + motivo(motivo), "taller-cotizaciones", cotizacion.getOrdenId());
        });
    }

    public void cotizacionVencida(Cotizacion cotizacion) {
        tallerRepository.findById(cotizacion.getTallerId()).ifPresent(taller -> {
            correoTaller(taller).ifPresent(correo -> enviar(correo, "Una cotización venció",
                    EmailServicioBuilder.tallerCotizacionVencida(taller.getNombre(),
                            cotizacion.getVehiculoDescripcion(), cotizacion.getVersion())));
            avisar(taller.getUsuarioId(), Notificacion.COTIZACION_VENCIDA, "Una cotización venció",
                    "La cotización v" + cotizacion.getVersion() + " de " + vehiculo(cotizacion.getVehiculoDescripcion())
                            + " venció sin respuesta.", "taller-cotizaciones", cotizacion.getOrdenId());
        });
    }

    /** Al pagar, una tienda de la cotización ya no vende (en pausa o archivada): el taller debe cotizar de nuevo. */
    public void cotizacionTiendaNoDisponible(Cotizacion cotizacion, String tiendaNombre) {
        tallerRepository.findById(cotizacion.getTallerId()).ifPresent(taller -> {
            String texto = "La cotización v" + cotizacion.getVersion() + " de " + vehiculo(cotizacion.getVehiculoDescripcion())
                    + " tiene repuestos de " + tiendaNombre + ", que ya no está disponible. No se cobró nada: emite una nueva cotización.";
            correoTaller(taller).ifPresent(correo -> enviar(correo, "Vuelve a cotizar: una tienda ya no está disponible",
                    EmailServicioBuilder.adminAlerta("Vuelve a cotizar", texto)));
            avisar(taller.getUsuarioId(), Notificacion.COTIZACION_TIENDA_NO_DISPONIBLE, "Vuelve a cotizar", texto,
                    "taller-cotizaciones", cotizacion.getOrdenId());
        });
    }

    // ─── Pagos del marketplace (Fase 3-B) ────────────────────────────────────

    public void comprobantePago(OrdenServicio orden, PagoEntity pago, String concepto,
                                Map<String, Double> lineas, String siguientePaso) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Comprobante de pago - DriveMaster",
                EmailServicioBuilder.clienteComprobantePago(c.getNombre(), concepto, orden.getVehiculoDescripcion(),
                        pago.getReferencia(), pago.getTransactionId(),
                        pago.getFechaPago() != null ? pago.getFechaPago() : LocalDateTime.now(),
                        pago.getMonto(), lineas, siguientePaso)));
        avisar(orden.getUsuarioId(), Notificacion.PAGO_APROBADO, "Pago aprobado",
                "Recibimos tu pago de " + concepto + ".", "mis-servicios", orden.getId());
    }

    /** Llegó un pago que no se pudo aplicar: aviso al cliente y alerta al ADMIN. */
    public void pagoNoAplicado(OrdenServicio orden, double monto, String motivo) {
        if (orden != null) {
            cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Te devolveremos tu pago",
                    EmailServicioBuilder.clientePagoNoAplicado(c.getNombre(), orden.getVehiculoDescripcion(), monto, motivo)));
            avisar(orden.getUsuarioId(), Notificacion.PAGO_NO_APLICADO, "Te devolveremos tu pago",
                    "No pudimos aplicar tu pago de " + Math.round(monto) + " COP" + motivo(motivo), "mis-servicios", orden.getId());
        }
        alertaAdmin("Reembolso pendiente", "Pago de " + Math.round(monto) + " COP"
                + (orden != null ? " (solicitud " + orden.getId() + ")" : "") + " sin aplicar: " + motivo);
    }

    public void diagnosticoPagado(OrdenServicio orden) {
        taller(orden).ifPresent(t -> {
            correoTaller(t).ifPresent(correo -> enviar(correo, "Diagnóstico pagado",
                    EmailServicioBuilder.tallerDiagnosticoPagado(t.getNombre(), orden.getVehiculoDescripcion(),
                            orden.getFechaProgramada())));
            avisar(t.getUsuarioId(), Notificacion.PAGO_APROBADO, "Diagnóstico pagado",
                    "El cliente pagó la revisión de " + vehiculo(orden) + ".", "taller-solicitudes", orden.getId());
        });
    }

    public void cotizacionPagada(OrdenServicio orden, List<PedidoTienda> pedidos) {
        taller(orden).ifPresent(t -> {
            correoTaller(t).ifPresent(correo -> enviar(correo, "El cliente pagó la cotización",
                    EmailServicioBuilder.tallerCotizacionPagada(t.getNombre(), orden.getVehiculoDescripcion(), pedidos.size())));
            avisar(t.getUsuarioId(), Notificacion.PAGO_APROBADO, "El cliente pagó la cotización",
                    vehiculo(orden) + (pedidos.isEmpty() ? "." : ": " + pedidos.size() + " pedido(s) de repuestos en camino."),
                    "taller-trabajos", orden.getId());
        });
        for (PedidoTienda p : pedidos) {
            tiendaRepository.findById(p.getTiendaId()).ifPresent(tienda -> {
                correoTienda(tienda).ifPresent(correo ->
                        enviar(correo, "Nuevo pedido pagado - " + p.getTallerNombre(),
                                EmailServicioBuilder.tiendaNuevoPedido(tienda.getNombre(), p)));
                avisar(tienda.getUsuarioId(), Notificacion.PEDIDO_NUEVO, "Nuevo pedido pagado",
                        "Pedido de " + p.getTallerNombre() + " por " + pesos(p.getTotal()) + ".", "pedidos-tienda", p.getId());
            });
        }
    }

    public void pedidoNoDespachado(OrdenServicio orden, PedidoTienda pedido) {
        double monto = pedido.getTotal() != null ? pedido.getTotal() : 0;
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Una tienda no pudo despachar tus repuestos",
                EmailServicioBuilder.clientePedidoNoDespachado(c.getNombre(), orden.getVehiculoDescripcion(),
                        pedido.getTiendaNombre(), monto)));
        taller(orden).ifPresent(t -> {
            correoTaller(t).ifPresent(correo -> enviar(correo, "Una tienda no puede despachar",
                    EmailServicioBuilder.tallerPedidoNoDespachado(t.getNombre(), orden.getVehiculoDescripcion(),
                            pedido.getTiendaNombre(), pedido.getMotivoNoDespacho())));
            avisar(t.getUsuarioId(), Notificacion.PEDIDO_NO_DESPACHADO, "Una tienda no puede despachar",
                    pedido.getTiendaNombre() + " no despachará los repuestos de " + vehiculo(orden) + ".",
                    "taller-trabajos", orden.getId());
        });
        avisar(orden.getUsuarioId(), Notificacion.PEDIDO_NO_DESPACHADO, "Una tienda no pudo despachar tus repuestos",
                pedido.getTiendaNombre() + " no despachará. Te devolveremos " + pesos(pedido.getTotal()) + ".",
                "mis-servicios", orden.getId());
        alertaAdmin("Tienda sin despacho", pedido.getTiendaNombre() + " no puede despachar el pedido de la solicitud "
                + orden.getId() + ". Reembolso pendiente: " + Math.round(monto) + " COP. Motivo: "
                + (pedido.getMotivoNoDespacho() != null ? pedido.getMotivoNoDespacho() : "sin motivo"));
    }

    // ─── Compras directas del catálogo ───────────────────────────────────────

    /** Comprobante al cliente (con sus códigos de retiro) y aviso a cada tienda. */
    public void compraPagada(com.drivemaster.drivemaster.model.CompraCatalogo compra, List<PedidoTienda> pedidos,
                             Map<String, String> codigos, PagoEntity pago) {
        if (registrarCodigosEnLog() && codigos != null) {
            codigos.forEach((pedidoId, codigo) -> log.info("[CORREO-DEV] Código de retiro del pedido {}: {}", pedidoId, codigo));
        }
        usuarioRepository.findById(compra.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Comprobante de tu compra - DriveMaster",
                EmailServicioBuilder.clienteCompraPagada(c.getNombre(), compra, pedidos, codigos, pago.getReferencia(),
                        pago.getTransactionId(), pago.getFechaPago() != null ? pago.getFechaPago() : LocalDateTime.now())));
        avisar(compra.getClienteId(), Notificacion.PAGO_APROBADO, "Compra confirmada",
                "Tu pago fue aprobado. Te enviamos el comprobante a tu correo.", "mis-compras", compra.getId());
        for (PedidoTienda p : pedidos) {
            String entrega = PedidoTienda.ENVIO.equals(p.getEntrega()) ? "envío" : "retiro";
            tiendaRepository.findById(p.getTiendaId()).ifPresent(tienda -> {
                correoTienda(tienda).ifPresent(correo ->
                        enviar(correo, "Nueva compra pagada - " + entrega,
                                EmailServicioBuilder.tiendaNuevaCompra(tienda.getNombre(), p)));
                avisar(tienda.getUsuarioId(), Notificacion.PEDIDO_NUEVO, "Nueva compra pagada (" + entrega + ")",
                        "Compra del catálogo por " + pesos(p.getTotal()) + ".", "pedidos-tienda", p.getId());
            });
        }
    }

    /** El pago llegó pero la compra no se pudo completar: aviso al cliente y alerta al ADMIN (reembolso completo). */
    public void compraNoAplicada(com.drivemaster.drivemaster.model.CompraCatalogo compra, double monto, String motivo) {
        usuarioRepository.findById(compra.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Te devolveremos tu pago",
                EmailServicioBuilder.clienteCompraNoAplicada(c.getNombre(), monto, motivo)));
        alertaAdmin("Reembolso pendiente", "Compra del catálogo " + compra.getId() + " sin aplicar. Reembolsar "
                + Math.round(monto) + " COP: " + motivo);
        avisar(compra.getClienteId(), Notificacion.PAGO_NO_APLICADO, "Te devolveremos tu pago",
                "No pudimos completar tu compra de " + Math.round(monto) + " COP" + motivo(motivo), "mis-compras", compra.getId());
    }

    /** La tienda no pudo despachar una compra directa: aviso al cliente (reembolso de esa tienda) y alerta al ADMIN. */
    public void pedidoCatalogoNoDespachado(PedidoTienda pedido) {
        double monto = pedido.getTotal() != null ? pedido.getTotal() : 0;
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(),
                "Una tienda no pudo despachar tu pedido",
                EmailServicioBuilder.clientePedidoNoDespachado(c.getNombre(), "compra del catálogo",
                        pedido.getTiendaNombre(), monto)));
        alertaAdmin("Tienda sin despacho", pedido.getTiendaNombre() + " no puede despachar el pedido " + pedido.getId()
                + " de una compra del catálogo. Reembolso pendiente: " + Math.round(monto) + " COP. Motivo: "
                + (pedido.getMotivoNoDespacho() != null ? pedido.getMotivoNoDespacho() : "sin motivo"));
        avisar(pedido.getClienteId(), Notificacion.PEDIDO_NO_DESPACHADO, "Una tienda no pudo despachar tu pedido",
                pedido.getTiendaNombre() + " no despachará. Te devolveremos " + pesos(pedido.getTotal()) + ".",
                "mis-compras", pedido.getCompraId());
    }

    /** La tienda despachó un envío: el cliente sabe que debe confirmar la recepción (o reportar un problema). */
    public void pedidoEnviado(PedidoTienda pedido, int diasAutoconfirmacion) {
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Tu pedido va en camino",
                EmailServicioBuilder.clienteEnvioEnCamino(c.getNombre(), pedido, diasAutoconfirmacion)));
        avisar(pedido.getClienteId(), Notificacion.PEDIDO_ENVIADO, "Tu pedido va en camino",
                pedido.getTiendaNombre() + " despachó tu pedido. Confirma cuando lo recibas.", "mis-compras", pedido.getCompraId());
    }

    /** El cliente reportó un problema con un envío: alerta al ADMIN y aviso a la tienda. */
    public void problemaEnvio(PedidoTienda pedido, String detalle) {
        alertaAdmin("Problema con un envío", "problemas-envios", "El cliente de la compra " + pedido.getCompraId() + " reportó un problema con el pedido "
                + pedido.getId() + " de " + pedido.getTiendaNombre() + " (" + Math.round(pedido.getTotal() != null ? pedido.getTotal() : 0)
                + " COP): " + detalle + ". La liquidación de la tienda está en pausa hasta que lo resuelvas.");
        tiendaRepository.findById(pedido.getTiendaId()).ifPresent(tienda -> {
            correoTienda(tienda).ifPresent(correo ->
                    enviar(correo, "El cliente reportó un problema con un envío",
                            EmailServicioBuilder.tiendaProblemaEnvio(tienda.getNombre(), pedido, detalle)));
            avisar(tienda.getUsuarioId(), Notificacion.PROBLEMA_ENVIO, "Problema reportado con un envío",
                    "El cliente reportó un problema con el pedido " + pedido.getId() + ".", "pedidos-tienda", pedido.getId());
        });
    }

    public void problemaEnvioResuelto(PedidoTienda pedido, boolean reembolsa) {
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Resolvimos el problema con tu pedido",
                EmailServicioBuilder.clienteProblemaEnvioResuelto(c.getNombre(), pedido.getTiendaNombre(), reembolsa,
                        pedido.getTotal() != null ? pedido.getTotal() : 0)));
        avisar(pedido.getClienteId(), Notificacion.PROBLEMA_ENVIO_RESUELTO, "Resolvimos el problema con tu pedido",
                reembolsa ? "Te devolveremos " + pesos(pedido.getTotal()) + "." : "Revisa el detalle en tu correo.",
                "mis-compras", pedido.getCompraId());
    }

    /** Código de retiro nuevo: se envía al titular de la compra (nunca se muestra en pantalla). */
    public void codigoRetiroNuevo(PedidoTienda pedido, String codigo) {
        if (registrarCodigosEnLog()) log.info("[CORREO-DEV] Código de retiro del pedido {}: {}", pedido.getId(), codigo);
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Tu nuevo código de retiro",
                EmailServicioBuilder.clienteCodigoRetiroNuevo(c.getNombre(), pedido, codigo)));
        avisar(pedido.getClienteId(), Notificacion.CODIGO_RETIRO, "Nuevo código de retiro",
                "Te enviamos un nuevo código de retiro a tu correo.", "mis-compras", pedido.getCompraId());
    }

    /** El pedido de retiro está listo: el cliente ya puede pasar por la tienda con su código. */
    public void pedidoListoParaRetirar(PedidoTienda pedido) {
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(),
                "Tu pedido está listo para retirar",
                EmailServicioBuilder.clientePedidoListoParaRetirar(c.getNombre(), pedido)));
        avisar(pedido.getClienteId(), Notificacion.PEDIDO_LISTO_RETIRO, "Tu pedido está listo para retirar",
                "Pasa por " + pedido.getTiendaNombre() + " con el código que te enviamos al correo.", "mis-compras", pedido.getCompraId());
    }

    /** El cliente no ha retirado su pedido listo: recordatorio por correo y en la campanita. */
    public void recordatorioRetiro(PedidoTienda pedido, long dias) {
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(),
                "Tu pedido te sigue esperando en " + pedido.getTiendaNombre(),
                EmailServicioBuilder.clienteRecordatorioRetiro(c.getNombre(), pedido, dias)));
        avisar(pedido.getClienteId(), Notificacion.RECORDATORIO_RETIRO, "Tu pedido te sigue esperando",
                pedido.getTiendaNombre() + " tiene listo tu pedido hace " + dias + " días. Pasa con tu código de retiro.",
                "mis-compras", pedido.getCompraId());
    }

    /** Un retiro lleva 15 días sin recogerse: lo revisa el ADMIN, que decide (no hay reembolso automático). */
    public void retiroEnRevision(PedidoTienda pedido, long dias) {
        alertaAdmin("Pedido sin retirar hace " + dias + " días", "problemas-envios",
                "El pedido " + pedido.getId() + " de " + pedido.getTiendaNombre() + " está listo para retirar desde hace " + dias
                        + " días y el cliente no lo ha recogido (ya recibió dos recordatorios). Total: "
                        + Math.round(pedido.getTotal() != null ? pedido.getTotal() : 0)
                        + " COP. Decide si se sigue esperando o se cancela con reembolso.");
    }

    /** El ADMIN canceló un retiro vencido con reembolso: se avisa al cliente. */
    public void retiroCancelado(PedidoTienda pedido) {
        double monto = pedido.getTotal() != null ? pedido.getTotal() : 0;
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(),
                "Cancelamos tu pedido sin retirar", EmailServicioBuilder.clienteRetiroCancelado(c.getNombre(), pedido, monto)));
        avisar(pedido.getClienteId(), Notificacion.RETIRO_CANCELADO, "Cancelamos tu pedido sin retirar",
                "No se retiró en " + pedido.getTiendaNombre() + ". Te devolveremos " + pesos(pedido.getTotal()) + ".",
                "mis-compras", pedido.getCompraId());
    }

    public void despachoAtrasado(PedidoTienda pedido) {
        alertaAdmin("Pedido sin despachar a tiempo", pedido.getTiendaNombre() + " no ha despachado el pedido "
                + pedido.getId() + " para " + pedido.getTallerNombre() + " (solicitud " + pedido.getOrdenId() + "). "
                + "Estado: " + pedido.getEstado() + ". Plazo vencido: "
                + (pedido.getPlazoDespachoHasta() != null ? pedido.getPlazoDespachoHasta().toLocalDate() : "sin plazo")
                + ". Total del pedido: " + Math.round(pedido.getTotal() != null ? pedido.getTotal() : 0) + " COP.");
    }

    public void vehiculoListo(OrdenServicio orden) {
        Taller t = taller(orden).orElse(null);
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Tu vehículo está listo",
                EmailServicioBuilder.clienteVehiculoListo(c.getNombre(), orden.getVehiculoDescripcion(),
                        t != null ? t.getNombre() : orden.getTallerNombre(), t != null ? t.getDireccion() : null)));
        avisar(orden.getUsuarioId(), Notificacion.VEHICULO_LISTO, "Tu vehículo está listo",
                vehiculo(orden) + " está listo en " + (t != null ? t.getNombre() : orden.getTallerNombre()) + ".",
                "mis-servicios", orden.getId());
    }

    public void alertaAdmin(String titulo, String detalle) {
        alertaAdmin(titulo, null, detalle);
    }

    /** Alerta por correo y en la campanita de cada ADMIN y SUPERADMIN activo; enlace es la página que la atiende. */
    public void alertaAdmin(String titulo, String enlace, String detalle) {
        for (String rol : List.of("ADMIN", "SUPERADMIN")) {
            usuarioRepository.findByRol(rol).forEach(admin -> {
                enviar(admin.getCorreo(), "[DriveMaster] " + titulo, EmailServicioBuilder.adminAlerta(titulo, detalle));
                if (!Boolean.FALSE.equals(admin.getActivo())) {
                    avisar(admin.getId(), Notificacion.ALERTA_ADMIN, titulo, detalle, enlace, null);
                }
            });
        }
    }

    // ─── Campanita ────────────────────────────────────────────────────────────

    /** El aviso es secundario: si falla, el flujo sigue (igual que con el correo). */
    private void avisar(String usuarioId, String tipo, String titulo, String mensaje, String enlace, String referenciaId) {
        try {
            notificaciones.avisar(usuarioId, tipo, titulo, mensaje, enlace, referenciaId);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el aviso '{}': {}", tipo, e.getMessage());
        }
    }

    private static String vehiculo(OrdenServicio orden) {
        return vehiculo(orden.getVehiculoDescripcion());
    }

    private static String vehiculo(String descripcion) {
        return descripcion != null && !descripcion.isBlank() ? descripcion : "el vehículo";
    }

    private static String motivo(String motivo) {
        return motivo != null && !motivo.isBlank() ? ": " + motivo : ".";
    }

    private static String pesos(Double monto) {
        return Math.round(monto != null ? monto : 0) + " COP";
    }

    private Optional<Taller> taller(OrdenServicio orden) {
        return orden.getTallerId() != null ? tallerRepository.findById(orden.getTallerId()) : Optional.empty();
    }

    private Optional<String> correoTienda(Tienda tienda) {
        if (tienda.getCorreoContacto() != null && !tienda.getCorreoContacto().isBlank()) {
            return Optional.of(tienda.getCorreoContacto());
        }
        return tienda.getUsuarioId() != null
                ? usuarioRepository.findById(tienda.getUsuarioId()).map(Usuario::getCorreo)
                : Optional.empty();
    }

    private Optional<Usuario> cliente(OrdenServicio orden) {
        return orden.getUsuarioId() != null ? usuarioRepository.findById(orden.getUsuarioId()) : Optional.empty();
    }

    private Optional<String> correoTaller(Taller taller) {
        return taller.getUsuarioId() != null
                ? usuarioRepository.findById(taller.getUsuarioId()).map(Usuario::getCorreo)
                : Optional.empty();
    }

    private String zona(OrdenServicio orden) {
        if (orden.getBarrio() != null && !orden.getBarrio().isBlank()) {
            return orden.getBarrio() + ", " + orden.getCiudad();
        }
        return orden.getCiudad();
    }

    private void enviar(String correo, String asunto, String html) {
        if (correo == null || correo.isBlank()) return;
        try {
            emailsService.enviarEmail(correo, asunto, html);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo '{}' a {}: {}", asunto, correo, e.getMessage());
        }
    }
}
