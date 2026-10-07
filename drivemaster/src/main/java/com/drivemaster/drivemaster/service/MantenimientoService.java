package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.VehiculoRepository;
import com.drivemaster.drivemaster.util.EmailMantenimientoBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MantenimientoService {

    private static final Logger log = LoggerFactory.getLogger(MantenimientoService.class);

    private final VehiculoRepository vehiculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailsService emailService;

    /** Días mínimos entre dos correos de recordatorio para el mismo vehículo. */
    @Value("${mantenimiento.diasEntreRecordatorios:30}")
    private int diasEntreRecordatorios;

    /** Intervalo por defecto al crear un vehículo. */
    @Value("${mantenimiento.intervalo.km.default:5000}")
    private int intervaloPorDefecto;

    public MantenimientoService(VehiculoRepository vehiculoRepository,
                                UsuarioRepository usuarioRepository,
                                EmailsService emailService) {
        this.vehiculoRepository = vehiculoRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
    }

    /** Normaliza los campos de mantenimiento de un vehículo recién creado. */
    public Vehiculo aplicarDefaults(Vehiculo vehiculo) {
        if (vehiculo.getIntervaloMantenimientoKm() == null
                || vehiculo.getIntervaloMantenimientoKm() <= 0) {
            vehiculo.setIntervaloMantenimientoKm(intervaloPorDefecto);
        }
        if (vehiculo.getRecordatoriosActivos() == null) {
            vehiculo.setRecordatoriosActivos(true);
        }
        if (vehiculo.getKmUltimoMantenimiento() == null && vehiculo.getKilometraje() != null) {
            // Sin historial previo, el kilometraje actual es la referencia inicial.
            vehiculo.setKmUltimoMantenimiento(vehiculo.getKilometraje());
        }
        return vehiculo;
    }

    /**
     * Registra que se hizo mantenimiento: reinicia el conteo de kilómetros.
     * Se llama al finalizar un servicio en taller.
     */
    public void registrarMantenimientoRealizado(Vehiculo vehiculo) {
        if (vehiculo.getKilometraje() == null) {
            return;
        }
        aplicarDefaults(vehiculo);
        vehiculo.setKmUltimoMantenimiento(vehiculo.getKilometraje());
        vehiculo.setUltimoRecordatorioEnviado(null);
        vehiculoRepository.save(vehiculo);
    }

    /** Revisa todos los vehículos y envía recordatorios a los que ya vencieron. */
    public int enviarRecordatoriosPendientes() {
        List<Vehiculo> vehiculos = vehiculoRepository.findAll();
        int enviados = 0;

        for (Vehiculo vehiculo : vehiculos) {
            try {
                if (enviarRecordatorioSiCorresponde(vehiculo)) {
                    enviados++;
                }
            } catch (Exception e) {
                // Un fallo en un vehículo no debe abortar el barrido completo.
                log.warn("No se pudo enviar el recordatorio del vehiculo {}: {}",
                        vehiculo.getId(), e.getMessage());
            }
        }

        log.info("Barrido de mantenimiento: {} recordatorios enviados de {} vehiculos",
                enviados, vehiculos.size());
        return enviados;
    }

    private boolean enviarRecordatorioSiCorresponde(Vehiculo vehiculo) {
        aplicarDefaults(vehiculo);

        if (Boolean.FALSE.equals(vehiculo.getRecordatoriosActivos())) {
            return false;
        }
        if (vehiculo.getKilometraje() == null || vehiculo.getKmUltimoMantenimiento() == null) {
            return false;
        }
        if (!estaVencido(vehiculo)) {
            return false;
        }
        if (yaSeAvisoRecientemente(vehiculo)) {
            return false;
        }

        Usuario usuario = usuarioRepository.findById(vehiculo.getUsuarioId())
                .orElse(null);
        if (usuario == null || usuario.getCorreo() == null) {
            return false;
        }

        String html = EmailMantenimientoBuilder.recordatorioKm(
                usuario, vehiculo,
                vehiculo.getKilometraje(),
                vehiculo.getKmUltimoMantenimiento(),
                vehiculo.getIntervaloMantenimientoKm());

        emailService.enviarEmail(
                usuario.getCorreo(),
                "Recordatorio de mantenimiento — DriveMaster",
                html);

        // Se marca el envío para no repetir el correo en cada corrida.
        vehiculo.setUltimoRecordatorioEnviado(LocalDateTime.now());
        vehiculoRepository.save(vehiculo);
        return true;
    }

    private boolean estaVencido(Vehiculo vehiculo) {
        int kmActual = vehiculo.getKilometraje();
        int ultimo = vehiculo.getKmUltimoMantenimiento();
        int intervalo = vehiculo.getIntervaloMantenimientoKm();
        return kmActual >= ultimo + intervalo;
    }

    private boolean yaSeAvisoRecientemente(Vehiculo vehiculo) {
        LocalDateTime ultimo = vehiculo.getUltimoRecordatorioEnviado();
        if (ultimo == null) {
            return false;
        }
        return ultimo.isAfter(LocalDateTime.now().minusDays(diasEntreRecordatorios));
    }

    /** Envia el aviso de mantenimiento registrado tras finalizar el servicio. */
    public void notificarMantenimientoRealizado(Vehiculo vehiculo) {
        Usuario usuario = usuarioRepository.findById(vehiculo.getUsuarioId()).orElse(null);
        if (usuario == null || usuario.getCorreo() == null) {
            return;
        }
        aplicarDefaults(vehiculo);

        String html = EmailMantenimientoBuilder.mantenimientoRealizado(
                usuario, vehiculo,
                vehiculo.getKilometraje(),
                vehiculo.getIntervaloMantenimientoKm());

        emailService.enviarEmail(
                usuario.getCorreo(),
                "Mantenimiento registrado — DriveMaster",
                html);
    }

    /** Envia el aviso con la fecha y hora de ingreso al taller. */
    public void notificarCitaProgramada(Usuario usuario, Vehiculo vehiculo,
                                        LocalDateTime fechaProgramada) {
        if (usuario.getCorreo() == null) {
            return;
        }
        String html = EmailMantenimientoBuilder.citaProgramada(usuario, vehiculo, fechaProgramada);
        emailService.enviarEmail(
                usuario.getCorreo(),
                "Tu cita en el taller está agendada — DriveMaster",
                html);
    }

    /** Envia el aviso de que el vehículo entró al taller. */
    public void notificarServicioIniciado(Usuario usuario, Vehiculo vehiculo, OrdenServicio orden) {
        if (usuario.getCorreo() == null) {
            return;
        }
        String html = EmailMantenimientoBuilder.servicioIniciado(usuario, vehiculo, orden);
        emailService.enviarEmail(
                usuario.getCorreo(),
                "Tu vehículo está en el taller — DriveMaster",
                html);
    }

    /** Envia el aviso de que el vehículo está listo para retirar. */
    public void notificarServicioFinalizado(Usuario usuario, Vehiculo vehiculo, OrdenServicio orden) {
        if (usuario.getCorreo() == null) {
            return;
        }
        String html = EmailMantenimientoBuilder.servicioFinalizado(usuario, vehiculo, orden);
        emailService.enviarEmail(
                usuario.getCorreo(),
                "Tu vehículo está listo — DriveMaster",
                html);
    }
}
