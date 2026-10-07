package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.mysql.*;
import com.drivemaster.drivemaster.repository.mysql.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// Corre antes que los seeders de Mongo y la migración del marketplace, que usan sus planes.
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class MysqlDataInitializer implements CommandLineRunner {

    private final MetodoPagoRepository metodoPagoRepo;
    private final EstadoVentaRepository estadoVentaRepo;
    private final ParametroRepository parametroRepo;
    private final PlanSuscripcionRepository planRepo;

    @Override
    public void run(String... args) {
        inicializarMetodosPago();
        inicializarEstados();
        inicializarParametros();
        inicializarPlanes();
        log.info("✅ MySQL inicializado correctamente");
    }

    /**
     * Planes de suscripción. Los precios arrancan en 0 (en v1 no hay cobro): el ADMIN
     * los define en PUT /api/suscripciones/planes/{codigo}. Solo se insertan si no existen.
     */
    private void inicializarPlanes() {
        List<Object[]> planes = List.of(
            new Object[]{PlanSuscripcion.PRUEBA,  "Prueba",  "Periodo de prueba para empezar a operar en DriveMaster", 30},
            new Object[]{PlanSuscripcion.BASICO,  "Básico",  "Operación estándar en el marketplace",                     null},
            new Object[]{PlanSuscripcion.PREMIUM, "Premium", "Beneficios adicionales; no altera el ranking de tiendas", null}
        );

        for (Object[] p : planes) {
            String codigo = (String) p[0];
            if (!planRepo.existsById(codigo)) {
                PlanSuscripcion plan = new PlanSuscripcion();
                plan.setCodigo(codigo);
                plan.setNombre((String) p[1]);
                plan.setDescripcion((String) p[2]);
                plan.setPrecioMensualTaller(0.0);
                plan.setPrecioMensualTienda(0.0);
                plan.setDuracionDias((Integer) p[3]);
                plan.setActivo(true);
                planRepo.save(plan);
                log.info("➕ Plan de suscripción insertado: {}", codigo);
            }
        }
    }

    private void inicializarMetodosPago() {
        List<Object[]> metodos = List.of(
            new Object[]{"EFECTIVO",      "Efectivo",      "💵", false},
            new Object[]{"TARJETA",       "Tarjeta",       "💳", true},
            new Object[]{"TRANSFERENCIA", "Transferencia", "🏦", true},
            new Object[]{"WOMPI",         "Pago en línea (Wompi)", "🟢", false}
        );

        for (Object[] m : metodos) {
            String codigo = (String) m[0];
            if (!metodoPagoRepo.existsByCodigo(codigo)) {
                MetodoPago mp = new MetodoPago();
                mp.setCodigo(codigo);
                mp.setNombre((String) m[1]);
                mp.setIcono((String) m[2]);
                mp.setActivo(true);
                mp.setRequiereReferencia((Boolean) m[3]);
                metodoPagoRepo.save(mp);
                log.info("➕ Método de pago insertado: {}", codigo);
            }
        }
    }

    private void inicializarEstados() {
        List<Object[]> estados = List.of(
            new Object[]{"PAGADA",              "Pagada"},
            new Object[]{"ANULADA",             "Anulada"},
            new Object[]{"PENDIENTE",           "Pendiente"},
            new Object[]{"CANCELADA",           "Cancelada"},
            new Object[]{"PENDIENTE_PAGO",      "Pendiente de pago"},
            new Object[]{"PAGO_VERIFICADO",     "Pago verificado"},
            new Object[]{"PENDIENTE_APROBACION","Pendiente de aprobación"},
            new Object[]{"APROBADO",            "Aprobado"},
            new Object[]{"RECHAZADO",           "Rechazado"},
            new Object[]{"EN_CAMINO",           "En camino"},
            new Object[]{"ENTREGADO",           "Entregado"}
        );

        for (Object[] e : estados) {
            String codigo = (String) e[0];
            if (estadoVentaRepo.findByCodigo(codigo).isEmpty()) {
                EstadoVenta ev = new EstadoVenta();
                ev.setCodigo(codigo);
                ev.setNombre((String) e[1]);
                ev.setActivo(true);
                estadoVentaRepo.save(ev);
                log.info("➕ Estado insertado: {}", codigo);
            }
        }
    }

    private void inicializarParametros() {
        List<Object[]> params = List.of(
    new Object[]{"IVA",                  "19",     "Porcentaje de impuesto (%)"},
    new Object[]{"TERMINAL_DEFAULT",     "POS-01", "Terminal por defecto"},
    new Object[]{"ESTADO_VENTA_DEFAULT", "PAGADA", "Estado por defecto al registrar venta"},
    // ─── Marketplace (valores iniciales; los ajusta el ADMIN) ───
    new Object[]{"TARIFA_DIAGNOSTICO",       "50000", "Tarifa fija de diagnóstico en COP (ingreso del taller, descontable de la reparación)"},
    new Object[]{"COMISION_PCT",             "10",    "Comisión de la plataforma (%) sobre el subtotal antes de IVA; en v1 solo se registra"},
    new Object[]{"TIMEOUT_TALLER_MIN",       "120",   "Minutos que tiene un taller para aceptar una solicitud antes de reasignarla"},
    new Object[]{"VIGENCIA_COTIZACION_DIAS", "7",     "Días de vigencia de una cotización"},
    new Object[]{"PAGO_DIAGNOSTICO_REQUERIDO",   "false", "Si es true, la solicitud pasa a revisión solo cuando el cliente paga el diagnóstico (Fase 3)"},
    new Object[]{"TIMEOUT_PAGO_DIAGNOSTICO_MIN", "1440",  "Minutos para pagar el diagnóstico tras la propuesta de cita (sin pasar la cita); si vence, se cancela"},
    new Object[]{"RADIO_MAX_KM",                 "30",    "Radio máximo (km) para asignar un taller fuera de la ciudad del cliente"},
    new Object[]{"HORA_INICIO_ATENCION",         "7",     "Hora (Colombia) en que empieza a correr el plazo de respuesta del taller (L-S)"},
    new Object[]{"HORA_FIN_ATENCION",            "19",    "Hora (Colombia) en que se detiene el plazo de respuesta del taller"}
);

        for (Object[] p : params) {
            String clave = (String) p[0];
            if (parametroRepo.findByClave(clave).isEmpty()) {
                Parametro param = new Parametro();
                param.setClave(clave);
                param.setValor((String) p[1]);
                param.setDescripcion((String) p[2]);
                parametroRepo.save(param);
                log.info("➕ Parámetro insertado: {}", clave);
            }
        }
    }

    
}