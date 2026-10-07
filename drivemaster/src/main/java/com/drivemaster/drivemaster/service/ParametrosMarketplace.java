package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lectura tipada de los parámetros del marketplace (tabla parametros). Cada valor
 * tiene un respaldo por si el parámetro no existe o está mal escrito, para que un
 * error de configuración no detenga el flujo.
 */
@Component
public class ParametrosMarketplace {

    private static final Logger log = LoggerFactory.getLogger(ParametrosMarketplace.class);

    private final ParametroRepository parametroRepository;

    public ParametrosMarketplace(ParametroRepository parametroRepository) {
        this.parametroRepository = parametroRepository;
    }

    /** Minutos hábiles que tiene un taller para aceptar o rechazar una solicitud. */
    public int timeoutTallerMin() {
        return entero("TIMEOUT_TALLER_MIN", 120);
    }

    /** Minutos que tiene el cliente para pagar el diagnóstico tras la propuesta de cita. */
    public int timeoutPagoDiagnosticoMin() {
        return entero("TIMEOUT_PAGO_DIAGNOSTICO_MIN", 1440);
    }

    /** Radio máximo (km) para buscar taller fuera de la ciudad del cliente. */
    public double radioMaxKm() {
        return decimal("RADIO_MAX_KM", 30);
    }

    /** Si es true, la solicitud solo pasa a EN_REVISION cuando se paga el diagnóstico. */
    public boolean pagoDiagnosticoRequerido() {
        return booleano("PAGO_DIAGNOSTICO_REQUERIDO", false);
    }

    public double tarifaDiagnostico() {
        return decimal("TARIFA_DIAGNOSTICO", 50000);
    }

    /** Horario de atención (hora de Colombia) en el que corre el plazo del taller. */
    public int horaInicioAtencion() {
        return entero("HORA_INICIO_ATENCION", 7);
    }

    public int horaFinAtencion() {
        return entero("HORA_FIN_ATENCION", 19);
    }

    private String valor(String clave) {
        return parametroRepository.findByClave(clave).map(p -> p.getValor()).orElse(null);
    }

    private int entero(String clave, int porDefecto) {
        String v = valor(clave);
        if (v == null) return porDefecto;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("Parámetro {} inválido ('{}'); se usa {}", clave, v, porDefecto);
            return porDefecto;
        }
    }

    private double decimal(String clave, double porDefecto) {
        String v = valor(clave);
        if (v == null) return porDefecto;
        try {
            return Double.parseDouble(v.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            log.warn("Parámetro {} inválido ('{}'); se usa {}", clave, v, porDefecto);
            return porDefecto;
        }
    }

    private boolean booleano(String clave, boolean porDefecto) {
        String v = valor(clave);
        if (v == null) return porDefecto;
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()) || "si".equalsIgnoreCase(v.trim());
    }
}
