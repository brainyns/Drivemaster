package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.service.RecepcionEnvioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Confirma sola la recepción de los envíos del catálogo cuyo cliente no respondió dentro del plazo
 * (PLAZO_AUTOCONFIRMACION_DIAS_HABILES días hábiles tras el despacho) y no reportó un problema.
 */
// Solo en el servidor que ejecuta las tareas de fondo: ver TAREAS_PROGRAMADAS_ENABLED en application.properties.
@ConditionalOnProperty(name = "drivemaster.tareas-programadas.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class RecepcionEnvioScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecepcionEnvioScheduler.class);

    private final RecepcionEnvioService recepcion;

    public RecepcionEnvioScheduler(RecepcionEnvioService recepcion) {
        this.recepcion = recepcion;
    }

    @Scheduled(fixedDelayString = "${marketplace.recepcion.delay-ms:900000}",
               initialDelayString = "${marketplace.recepcion.initial-delay-ms:120000}")
    public void autoconfirmar() {
        try {
            int n = recepcion.autoconfirmar(LocalDateTime.now());
            if (n > 0) log.info("Recepción de envíos: {} autoconfirmado(s)", n);
        } catch (Exception e) {
            log.error("Recepción de envíos: no se pudo procesar la autoconfirmación", e);
        }
    }
}
