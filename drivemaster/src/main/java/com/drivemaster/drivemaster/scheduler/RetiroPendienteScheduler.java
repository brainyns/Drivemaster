package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.service.RetiroPendienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * Recuerda al cliente los pedidos listos que no retira (a los 3 y 7 días) y, a los 15, los pasa a revisión del ADMIN.
 * Ver RetiroPendienteService.
 */
// Solo en el servidor que ejecuta las tareas de fondo: ver TAREAS_PROGRAMADAS_ENABLED en application.properties.
@ConditionalOnProperty(name = "drivemaster.tareas-programadas.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class RetiroPendienteScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetiroPendienteScheduler.class);

    private final RetiroPendienteService retiros;

    public RetiroPendienteScheduler(RetiroPendienteService retiros) {
        this.retiros = retiros;
    }

    @Scheduled(fixedDelayString = "${marketplace.retiros.delay-ms:3600000}",
               initialDelayString = "${marketplace.retiros.initial-delay-ms:180000}")
    public void revisar() {
        try {
            int n = retiros.procesar(LocalDateTime.now());
            if (n > 0) log.info("Retiros sin recoger: {} recordatorio(s) o revisión(es)", n);
        } catch (Exception e) {
            log.error("Retiros sin recoger: no se pudo procesar el ciclo", e);
        }
    }
}
