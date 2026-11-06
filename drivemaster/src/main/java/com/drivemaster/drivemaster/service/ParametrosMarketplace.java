package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lectura tipada de los parámetros del marketplace (tabla parametros). Cada valor
 * tiene un respaldo por si el parámetro no existe o está mal escrito, para que un
 * error de configuración no detenga el flujo.
 */
@Component
public class ParametrosMarketplace {

    private static final Logger log = LoggerFactory.getLogger(ParametrosMarketplace.class);

    static final double COMISION_MAXIMA = 50;
    private static final double COMISION_POR_DEFECTO = 10;

    private final ParametroRepository parametroRepository;

    // Variable de entorno y no parámetro: la tabla parametros la comparten todos los ambientes y su PUT no deja registro
    @Value("${drivemaster.comision-retenida.enabled:false}")
    private boolean comisionRetenida;

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

    /** Porcentaje de IVA (parámetro IVA existente, editable por el ADMIN). */
    public double ivaPct() {
        return decimal("IVA", 19);
    }

    /**
     * Comisión de la plataforma (%) sobre el precio con IVA, sin envío. Siempre dentro de 0 a {@value #COMISION_MAXIMA}: un valor
     * escrito directo en la base no puede llevarse el dinero del negocio.
     */
    public double comisionPct() {
        double pct = decimal("COMISION_PCT", COMISION_POR_DEFECTO);
        if (Double.isNaN(pct)) return COMISION_POR_DEFECTO;
        double valida = Math.max(0, Math.min(COMISION_MAXIMA, pct));
        if (valida != pct) log.warn("COMISION_PCT fuera de rango ({}); se usa {}", pct, valida);
        return valida;
    }

    /** Si la comisión se descuenta de lo que recibe el negocio (true) o solo se registra (false). Se lee al aprobar cada pago. */
    public boolean comisionRetenida() {
        return comisionRetenida;
    }

    /** Si un valor escrito por el ADMIN sirve como COMISION_PCT: un número de 0 a {@value #COMISION_MAXIMA}. */
    public static boolean comisionValida(String valor) {
        if (valor == null) return false;
        try {
            double pct = Double.parseDouble(valor.trim().replace(",", "."));
            return pct >= 0 && pct <= COMISION_MAXIMA;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public int vigenciaCotizacionDias() {
        return entero("VIGENCIA_COTIZACION_DIAS", 7);
    }

    /** Horario de atención (hora de Colombia) en el que corre el plazo del taller. */
    public int horaInicioAtencion() {
        return entero("HORA_INICIO_ATENCION", 7);
    }

    public int horaFinAtencion() {
        return entero("HORA_FIN_ATENCION", 19);
    }

    /** Días hábiles para despachar un pedido con repuestos en stock (cuenta desde el pago). */
    public int plazoDespachoDiasHabiles() {
        return entero("PLAZO_DESPACHO_DIAS_HABILES", 2);
    }

    /** Días hábiles tras el despacho de un envío para que el cliente confirme; si no responde se confirma sola. */
    public int plazoAutoconfirmacionDiasHabiles() {
        return entero("PLAZO_AUTOCONFIRMACION_DIAS_HABILES", 5);
    }

    /** Días hábiles tras la entrega de un vehículo para que el cliente confirme; si no responde se confirma sola. */
    public int plazoRecepcionServicioDiasHabiles() {
        return entero("PLAZO_RECEPCION_SERVICIO_DIAS_HABILES", 3);
    }

    /** Días hábiles que se suman a los días estimados de un encargo. */
    public int plazoEncargoDiasExtra() {
        return entero("PLAZO_ENCARGO_DIAS_EXTRA", 2);
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
