package com.drivemaster.drivemaster.model;

import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de las órdenes del marketplace (OrdenServicio con flujo
 * MARKETPLACE). Las órdenes del flujo anterior (V1) conservan sus propios estados.
 *
 * La tabla TRANSICIONES es la única fuente de verdad de qué cambio de estado es
 * válido y quién puede ejecutarlo. Las fases siguientes usan las transiciones
 * desde DIAGNOSTICADA en adelante.
 */
public final class EstadoOrden {

    public static final String FLUJO_MARKETPLACE = "MARKETPLACE";

    // ─── Estados ───
    public static final String ENVIADA = "ENVIADA";
    public static final String ASIGNADA_A_TALLER = "ASIGNADA_A_TALLER";
    public static final String REASIGNADA = "REASIGNADA";
    public static final String EN_REVISION = "EN_REVISION";
    public static final String DIAGNOSTICADA = "DIAGNOSTICADA";
    public static final String COTIZADA = "COTIZADA";
    public static final String APROBADA = "APROBADA";
    public static final String PAGADA = "PAGADA";
    public static final String REPUESTOS_PEDIDOS = "REPUESTOS_PEDIDOS";
    public static final String REPUESTOS_RECIBIDOS = "REPUESTOS_RECIBIDOS";
    public static final String EN_INSTALACION = "EN_INSTALACION";
    public static final String LISTA = "LISTA";
    public static final String ENTREGADA = "ENTREGADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CANCELADA = "CANCELADA";

    /** Estados en los que la orden sigue abierta (un vehículo no puede tener dos a la vez). */
    public static final Set<String> ACTIVOS = Set.of(
            ENVIADA, ASIGNADA_A_TALLER, REASIGNADA, EN_REVISION, DIAGNOSTICADA, COTIZADA, APROBADA,
            PAGADA, REPUESTOS_PEDIDOS, REPUESTOS_RECIBIDOS, EN_INSTALACION, LISTA);

    // ─── Actores ───
    public static final String SISTEMA = "SISTEMA";
    public static final String CLIENTE = "CLIENTE";
    public static final String TALLER = "TALLER";
    public static final String MECANICO = "MECANICO";
    public static final String ADMIN = "ADMIN";

    // ─── Pago de la tarifa de diagnóstico ───
    public static final String PAGO_NO_REQUERIDO = "NO_REQUERIDO";
    public static final String PAGO_PENDIENTE = "PENDIENTE";
    public static final String PAGO_PAGADO = "PAGADO";
    public static final String PAGO_VENCIDO = "VENCIDO";

    /** estado origen → (estado destino → actores que pueden ejecutarlo) */
    private static final Map<String, Map<String, Set<String>>> TRANSICIONES = Map.ofEntries(
            Map.entry(ENVIADA, Map.of(
                    ASIGNADA_A_TALLER, Set.of(SISTEMA),
                    RECHAZADA, Set.of(SISTEMA),
                    CANCELADA, Set.of(CLIENTE, ADMIN))),
            Map.entry(ASIGNADA_A_TALLER, Map.of(
                    // El taller la rechaza, no responde a tiempo o el ADMIN la mueve
                    REASIGNADA, Set.of(SISTEMA, TALLER, ADMIN),
                    // El taller acepta (sin pago requerido) o el sistema confirma el pago del diagnóstico
                    EN_REVISION, Set.of(TALLER, SISTEMA),
                    // El cliente desiste o vence el plazo de pago del diagnóstico
                    CANCELADA, Set.of(CLIENTE, ADMIN, SISTEMA))),
            Map.entry(REASIGNADA, Map.of(
                    ASIGNADA_A_TALLER, Set.of(SISTEMA),
                    RECHAZADA, Set.of(SISTEMA),
                    CANCELADA, Set.of(CLIENTE, ADMIN))),
            Map.entry(EN_REVISION, Map.of(
                    DIAGNOSTICADA, Set.of(TALLER, MECANICO),
                    // Con el diagnóstico ya pagado, solo el ADMIN cancela (gestiona el reembolso)
                    CANCELADA, Set.of(ADMIN))),
            Map.entry(DIAGNOSTICADA, Map.of(
                    COTIZADA, Set.of(TALLER, MECANICO),
                    CANCELADA, Set.of(CLIENTE, ADMIN))),
            Map.entry(COTIZADA, Map.of(
                    APROBADA, Set.of(CLIENTE),
                    // El cliente pide cambios o la cotización vence
                    DIAGNOSTICADA, Set.of(CLIENTE, SISTEMA),
                    // El cliente la rechaza: solo paga el diagnóstico
                    CANCELADA, Set.of(CLIENTE, ADMIN))),
            Map.entry(APROBADA, Map.of(
                    PAGADA, Set.of(SISTEMA),
                    // Falla el pago: vuelve a COTIZADA, sin descuentos ni pedidos
                    COTIZADA, Set.of(SISTEMA),
                    CANCELADA, Set.of(CLIENTE, ADMIN))),
            Map.entry(PAGADA, Map.of(
                    REPUESTOS_PEDIDOS, Set.of(SISTEMA),
                    // Cotización sin repuestos (solo mano de obra)
                    REPUESTOS_RECIBIDOS, Set.of(SISTEMA),
                    // Falla la revalidación de stock al confirmar el pago: reembolso y se recotiza
                    DIAGNOSTICADA, Set.of(SISTEMA),
                    CANCELADA, Set.of(ADMIN))),
            Map.entry(REPUESTOS_PEDIDOS, Map.of(
                    REPUESTOS_RECIBIDOS, Set.of(TALLER),
                    CANCELADA, Set.of(ADMIN))),
            Map.entry(REPUESTOS_RECIBIDOS, Map.of(
                    EN_INSTALACION, Set.of(TALLER, MECANICO))),
            Map.entry(EN_INSTALACION, Map.of(
                    LISTA, Set.of(TALLER, MECANICO))),
            Map.entry(LISTA, Map.of(
                    ENTREGADA, Set.of(TALLER)))
    );

    private EstadoOrden() {
    }

    public static boolean permitida(String desde, String hacia, String actor) {
        Map<String, Set<String>> destinos = TRANSICIONES.get(desde);
        if (destinos == null) return false;
        Set<String> actores = destinos.get(hacia);
        return actores != null && actores.contains(actor);
    }

    /** Rol de la cuenta → actor de la máquina de estados. */
    public static String actorDeRol(String rol) {
        if (rol == null) return null;
        return switch (rol) {
            case "SUPERADMIN", "ADMIN" -> ADMIN;
            case "CLIENTE" -> CLIENTE;
            case "TALLER" -> TALLER;
            case "MECANICO" -> MECANICO;
            default -> rol;
        };
    }
}
