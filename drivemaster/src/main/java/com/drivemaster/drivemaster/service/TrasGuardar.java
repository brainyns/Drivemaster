package com.drivemaster.drivemaster.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lo que sigue a un cambio de estado ya guardado (avisos, pagos que se habilitan). Si falla, el cambio sigue hecho: un
 * 500 le diría al usuario que no pasó y reintentaría sobre un estado que ya avanzó. Queda el log y la alerta al ADMIN.
 */
final class TrasGuardar {

    private static final Logger log = LoggerFactory.getLogger(TrasGuardar.class);

    private TrasGuardar() {
    }

    /** queQuedoPendiente: lo que el ADMIN debe resolver, en una frase. Nunca lanza. */
    static void hacer(AvisosSolicitudServicio avisos, String queQuedoPendiente, Runnable paso) {
        try {
            paso.run();
        } catch (RuntimeException e) {
            log.error("Cambio guardado con un paso pendiente: {}", queQuedoPendiente, e);
            try {
                avisos.alertaAdmin("Paso sin completar", queQuedoPendiente);
            } catch (RuntimeException sinAlerta) {
                log.error("Tampoco se pudo alertar al ADMIN: {}", queQuedoPendiente, sinAlerta);
            }
        }
    }
}
