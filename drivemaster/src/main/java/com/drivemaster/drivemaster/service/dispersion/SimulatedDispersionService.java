package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.util.Dinero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dispersión de desarrollo: no mueve dinero, solo genera una referencia SIM-… y deja
 * la línea LIQUIDADO con modo SIMULADO. DispersionConfig solo la crea con llaves de
 * pruebas de Wompi.
 */
public class SimulatedDispersionService implements DispersionService {

    public static final String MODO = "SIMULADO";

    @Override
    public String modo() {
        return MODO;
    }

    @Override
    public boolean automatica() {
        return true;
    }

    @Override
    public ResultadoDispersion liquidar(RepartoPago reparto, SolicitudDispersion solicitud) {
        BigDecimal monto = Dinero.pesos(reparto.getMonto());
        if (monto.signum() <= 0) {
            throw new DispersionException("El monto a liquidar debe ser mayor que cero");
        }
        String referencia = "SIM-" + reparto.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ResultadoDispersion(MODO, referencia, monto);
    }
}
