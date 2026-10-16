package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.service.ResumenSemanalTienda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResumenSemanalScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumenSemanalScheduler.class);

    private final ResumenSemanalTienda resumen;

    public ResumenSemanalScheduler(ResumenSemanalTienda resumen) {
        this.resumen = resumen;
    }

    /** Lunes 7:00 en Colombia. El servicio salta las tiendas que ya lo recibieron esta semana. */
    @Scheduled(cron = "${tienda.resumen-semanal.cron:0 0 7 * * MON}", zone = "America/Bogota")
    public void enviar() {
        try {
            log.info("Resumen semanal de tiendas: {} correos enviados", resumen.enviarPendientes());
        } catch (Exception e) {
            // Un fallo puntual no debe matar el hilo del scheduler.
            log.error("Error en el job del resumen semanal de tiendas", e);
        }
    }
}
