package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
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
 * Avisos por correo del flujo de solicitudes. Un fallo de envío nunca revierte el
 * cambio de estado: se registra en el log y el flujo sigue.
 */
@Service
public class AvisosSolicitudServicio {

    private static final Logger log = LoggerFactory.getLogger(AvisosSolicitudServicio.class);

    private final EmailsService emailsService;
    private final UsuarioRepository usuarioRepository;
    private final TallerRepository tallerRepository;
    private final TiendaRepository tiendaRepository;
    private final String frontendUrl;

    public AvisosSolicitudServicio(EmailsService emailsService,
                                   UsuarioRepository usuarioRepository,
                                   TallerRepository tallerRepository,
                                   TiendaRepository tiendaRepository,
                                   @Value("${frontend.url}") String frontendUrl) {
        this.tiendaRepository = tiendaRepository;
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
    }

    public void sinTallerDisponible(OrdenServicio orden) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "No encontramos taller disponible",
                EmailServicioBuilder.clienteSinTaller(c.getNombre(), orden.getVehiculoDescripcion())));
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
    }

    public void solicitudCancelada(OrdenServicio orden, String motivo) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Tu solicitud fue cancelada",
                EmailServicioBuilder.clienteSolicitudCancelada(c.getNombre(), orden.getVehiculoDescripcion(), motivo)));
    }

    /** Aviso al taller que pierde una solicitud (reasignada o cancelada). */
    public void solicitudRetirada(String tallerId, OrdenServicio orden, String motivo) {
        if (tallerId == null) return;
        tallerRepository.findById(tallerId).ifPresent(taller ->
                correoTaller(taller).ifPresent(correo -> enviar(correo, "Una solicitud salió de tu bandeja",
                        EmailServicioBuilder.tallerSolicitudRetirada(taller.getNombre(),
                                orden.getVehiculoDescripcion(), motivo))));
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
    }

    public void cotizacionRespondida(Cotizacion cotizacion, String respuesta, String motivo) {
        tallerRepository.findById(cotizacion.getTallerId()).ifPresent(taller ->
                correoTaller(taller).ifPresent(correo -> enviar(correo, "Respuesta a tu cotización: " + respuesta,
                        EmailServicioBuilder.tallerRespuestaCotizacion(taller.getNombre(),
                                cotizacion.getVehiculoDescripcion(), cotizacion.getVersion(), respuesta, motivo))));
    }

    public void cotizacionVencida(Cotizacion cotizacion) {
        tallerRepository.findById(cotizacion.getTallerId()).ifPresent(taller ->
                correoTaller(taller).ifPresent(correo -> enviar(correo, "Una cotización venció",
                        EmailServicioBuilder.tallerCotizacionVencida(taller.getNombre(),
                                cotizacion.getVehiculoDescripcion(), cotizacion.getVersion()))));
    }

    // ─── Pagos del marketplace (Fase 3-B) ────────────────────────────────────

    public void comprobantePago(OrdenServicio orden, PagoEntity pago, String concepto,
                                Map<String, Double> lineas, String siguientePaso) {
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Comprobante de pago - DriveMaster",
                EmailServicioBuilder.clienteComprobantePago(c.getNombre(), concepto, orden.getVehiculoDescripcion(),
                        pago.getReferencia(), pago.getTransactionId(),
                        pago.getFechaPago() != null ? pago.getFechaPago() : LocalDateTime.now(),
                        pago.getMonto(), lineas, siguientePaso)));
    }

    /** Llegó un pago que no se pudo aplicar: aviso al cliente y alerta al ADMIN. */
    public void pagoNoAplicado(OrdenServicio orden, double monto, String motivo) {
        if (orden != null) {
            cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Te devolveremos tu pago",
                    EmailServicioBuilder.clientePagoNoAplicado(c.getNombre(), orden.getVehiculoDescripcion(), monto, motivo)));
        }
        alertaAdmin("Reembolso pendiente", "Pago de " + Math.round(monto) + " COP"
                + (orden != null ? " (solicitud " + orden.getId() + ")" : "") + " sin aplicar: " + motivo);
    }

    public void diagnosticoPagado(OrdenServicio orden) {
        taller(orden).ifPresent(t -> correoTaller(t).ifPresent(correo -> enviar(correo, "Diagnóstico pagado",
                EmailServicioBuilder.tallerDiagnosticoPagado(t.getNombre(), orden.getVehiculoDescripcion(),
                        orden.getFechaProgramada()))));
    }

    public void cotizacionPagada(OrdenServicio orden, List<PedidoTienda> pedidos) {
        taller(orden).ifPresent(t -> correoTaller(t).ifPresent(correo -> enviar(correo, "El cliente pagó la cotización",
                EmailServicioBuilder.tallerCotizacionPagada(t.getNombre(), orden.getVehiculoDescripcion(), pedidos.size()))));
        for (PedidoTienda p : pedidos) {
            tiendaRepository.findById(p.getTiendaId()).ifPresent(tienda -> correoTienda(tienda).ifPresent(correo ->
                    enviar(correo, "Nuevo pedido pagado - " + p.getTallerNombre(),
                            EmailServicioBuilder.tiendaNuevoPedido(tienda.getNombre(), p))));
        }
    }

    public void pedidoNoDespachado(OrdenServicio orden, PedidoTienda pedido) {
        double monto = pedido.getTotal() != null ? pedido.getTotal() : 0;
        cliente(orden).ifPresent(c -> enviar(c.getCorreo(), "Una tienda no pudo despachar tus repuestos",
                EmailServicioBuilder.clientePedidoNoDespachado(c.getNombre(), orden.getVehiculoDescripcion(),
                        pedido.getTiendaNombre(), monto)));
        taller(orden).ifPresent(t -> correoTaller(t).ifPresent(correo -> enviar(correo, "Una tienda no puede despachar",
                EmailServicioBuilder.tallerPedidoNoDespachado(t.getNombre(), orden.getVehiculoDescripcion(),
                        pedido.getTiendaNombre(), pedido.getMotivoNoDespacho()))));
        alertaAdmin("Tienda sin despacho", pedido.getTiendaNombre() + " no puede despachar el pedido de la solicitud "
                + orden.getId() + ". Reembolso pendiente: " + Math.round(monto) + " COP. Motivo: "
                + (pedido.getMotivoNoDespacho() != null ? pedido.getMotivoNoDespacho() : "sin motivo"));
    }

    // ─── Compras directas del catálogo ───────────────────────────────────────

    /** Comprobante al cliente (con sus códigos de retiro) y aviso a cada tienda. */
    public void compraPagada(com.drivemaster.drivemaster.model.CompraCatalogo compra, List<PedidoTienda> pedidos,
                             Map<String, String> codigos, PagoEntity pago) {
        usuarioRepository.findById(compra.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Comprobante de tu compra - DriveMaster",
                EmailServicioBuilder.clienteCompraPagada(c.getNombre(), compra, pedidos, codigos, pago.getReferencia(),
                        pago.getTransactionId(), pago.getFechaPago() != null ? pago.getFechaPago() : LocalDateTime.now())));
        for (PedidoTienda p : pedidos) {
            tiendaRepository.findById(p.getTiendaId()).ifPresent(tienda -> correoTienda(tienda).ifPresent(correo ->
                    enviar(correo, "Nueva compra pagada - " + (PedidoTienda.ENVIO.equals(p.getEntrega()) ? "envío" : "retiro"),
                            EmailServicioBuilder.tiendaNuevaCompra(tienda.getNombre(), p))));
        }
    }

    /** El pago llegó pero la compra no se pudo completar: aviso al cliente y alerta al ADMIN (reembolso completo). */
    public void compraNoAplicada(com.drivemaster.drivemaster.model.CompraCatalogo compra, double monto, String motivo) {
        usuarioRepository.findById(compra.getClienteId()).ifPresent(c -> enviar(c.getCorreo(), "Te devolveremos tu pago",
                EmailServicioBuilder.clienteCompraNoAplicada(c.getNombre(), monto, motivo)));
        alertaAdmin("Reembolso pendiente", "Compra del catálogo " + compra.getId() + " sin aplicar. Reembolsar "
                + Math.round(monto) + " COP: " + motivo);
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
    }

    /** El pedido de retiro está listo: el cliente ya puede pasar por la tienda con su código. */
    public void pedidoListoParaRetirar(PedidoTienda pedido) {
        usuarioRepository.findById(pedido.getClienteId()).ifPresent(c -> enviar(c.getCorreo(),
                "Tu pedido está listo para retirar",
                EmailServicioBuilder.clientePedidoListoParaRetirar(c.getNombre(), pedido)));
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
    }

    public void alertaAdmin(String titulo, String detalle) {
        for (String rol : List.of("ADMIN", "SUPERADMIN")) {
            usuarioRepository.findByRol(rol).forEach(admin ->
                    enviar(admin.getCorreo(), "[DriveMaster] " + titulo, EmailServicioBuilder.adminAlerta(titulo, detalle)));
        }
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
