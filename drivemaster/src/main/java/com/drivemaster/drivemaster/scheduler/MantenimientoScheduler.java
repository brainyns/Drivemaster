package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.service.MantenimientoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Solo en el servidor que ejecuta las tareas de fondo: ver TAREAS_PROGRAMADAS_ENABLED en application.properties.
@ConditionalOnProperty(name = "drivemaster.tareas-programadas.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class MantenimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(MantenimientoScheduler.class);

    private final MantenimientoService mantenimientoService;

    public MantenimientoScheduler(MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    /**
     * Barre los recordatorios de mantenimiento una vez al día a las 08:00.
     * El propio servicio se encarga del opt-out y de no repetir el correo
     * dentro de la ventana de diasEntreRecordatorios.
     */
    @Scheduled(cron = "${mantenimiento.cron:0 0 8 * * *}")
    public void enviarRecordatorios() {
        try {
            mantenimientoService.enviarRecordatoriosPendientes();
        } catch (Exception e) {
            // Un fallo puntual no debe matar el hilo del scheduler.
            log.error("Error en el job de recordatorios de mantenimiento", e);
        }
    }
}
