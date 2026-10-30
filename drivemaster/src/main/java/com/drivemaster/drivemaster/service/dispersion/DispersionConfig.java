package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Elige la dispersión según DISPERSION_MODO: SIMULADO, MANUAL o WOMPI.
 * El modo SIMULADO solo arranca con llaves de pruebas de Wompi (pub_test_): con llaves reales
 * el backend exige DISPERSION_MODO=MANUAL o DISPERSION_MODO=WOMPI.
 */
@Configuration
public class DispersionConfig {

    private static final Logger log = LoggerFactory.getLogger(DispersionConfig.class);

    @Bean
    public DispersionService dispersionService(
            @Value("${dispersion.modo:SIMULADO}") String modo,
            @Value("${wompi.public_key:}") String wompiPublicKey,
            @Value("${wompi.payouts.api_key:}") String apiKey,
            @Value("${wompi.payouts.user_principal_id:}") String userPrincipalId,
            @Value("${wompi.payouts.api_url:https://api.sandbox.payouts.wompi.co/v2}") String payoutsApiUrl,
            TallerRepository tallerRepository,
            TiendaRepository tiendaRepository,
            @Autowired(required = false) RestTemplate restTemplate) {

        WompiPayoutService wompiService = new WompiPayoutService(
                tallerRepository, tiendaRepository, restTemplate,
                apiKey, userPrincipalId, payoutsApiUrl, wompiPublicKey
        );

        DispersionService servicio = crear(modo, wompiPublicKey, wompiService);
        if (SimulatedDispersionService.MODO.equals(servicio.modo())) {
            log.warn("DISPERSION SIMULADA ACTIVA: las liquidaciones no mueven dinero. Solo para desarrollo.");
        } else if (WompiPayoutService.MODO.equals(servicio.modo())) {
            log.info("Dispersión en modo WOMPI (API Payouts: {})", payoutsApiUrl);
        } else {
            log.info("Dispersión en modo {}", servicio.modo());
        }
        return servicio;
    }

    static DispersionService crear(String modo, String wompiPublicKey) {
        return crear(modo, wompiPublicKey, null);
    }

    static DispersionService crear(String modo, String wompiPublicKey, WompiPayoutService wompiPayoutService) {
        String m = modo != null ? modo.trim().toUpperCase() : "";
        switch (m) {
            case SimulatedDispersionService.MODO:
                if (wompiPublicKey == null || !wompiPublicKey.startsWith("pub_test_")) {
                    throw new IllegalStateException("DISPERSION_MODO=SIMULADO solo funciona con llaves de pruebas de Wompi "
                            + "(pub_test_). Con llaves reales usa DISPERSION_MODO=MANUAL o DISPERSION_MODO=WOMPI.");
                }
                return new SimulatedDispersionService();
            case ManualDispersionService.MODO:
                return new ManualDispersionService();
            case WompiPayoutService.MODO:
                if (wompiPayoutService != null) {
                    return wompiPayoutService;
                }
                throw new IllegalStateException("Se requiere WompiPayoutService configurado para DISPERSION_MODO=WOMPI");
            default:
                throw new IllegalStateException("DISPERSION_MODO inválido: '" + modo + "'. Valores: SIMULADO, MANUAL o WOMPI.");
        }
    }
}
