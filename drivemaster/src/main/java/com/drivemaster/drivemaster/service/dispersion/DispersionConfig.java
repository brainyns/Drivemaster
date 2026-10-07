package com.drivemaster.drivemaster.service.dispersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige la dispersión según DISPERSION_MODO. El modo SIMULADO solo arranca con llaves
 * de pruebas de Wompi (pub_test_): con llaves reales el backend se niega a iniciar, para
 * que nunca se "liquide" con dinero de mentira en producción.
 *
 * Aquí se agregaría un modo WOMPI para la futura WompiPayoutService (Pagos a Terceros).
 */
@Configuration
public class DispersionConfig {

    private static final Logger log = LoggerFactory.getLogger(DispersionConfig.class);

    @Bean
    public DispersionService dispersionService(@Value("${dispersion.modo:SIMULADO}") String modo,
                                               @Value("${wompi.public_key:}") String wompiPublicKey) {
        DispersionService servicio = crear(modo, wompiPublicKey);
        if (SimulatedDispersionService.MODO.equals(servicio.modo())) {
            log.warn("DISPERSION SIMULADA ACTIVA: las liquidaciones no mueven dinero. Solo para desarrollo.");
        } else {
            log.info("Dispersión en modo {}", servicio.modo());
        }
        return servicio;
    }

    static DispersionService crear(String modo, String wompiPublicKey) {
        String m = modo != null ? modo.trim().toUpperCase() : "";
        switch (m) {
            case SimulatedDispersionService.MODO:
                if (wompiPublicKey == null || !wompiPublicKey.startsWith("pub_test_")) {
                    throw new IllegalStateException("DISPERSION_MODO=SIMULADO solo funciona con llaves de pruebas de Wompi "
                            + "(pub_test_). Con llaves reales usa DISPERSION_MODO=MANUAL.");
                }
                return new SimulatedDispersionService();
            case ManualDispersionService.MODO:
                return new ManualDispersionService();
            default:
                throw new IllegalStateException("DISPERSION_MODO inválido: '" + modo + "'. Valores: SIMULADO o MANUAL.");
        }
    }
}
