package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.EmailServicioBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final String frontendUrl;

    public AvisosSolicitudServicio(EmailsService emailsService,
                                   UsuarioRepository usuarioRepository,
                                   TallerRepository tallerRepository,
                                   @Value("${frontend.url}") String frontendUrl) {
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
