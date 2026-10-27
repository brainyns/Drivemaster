package com.drivemaster.drivemaster.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import com.drivemaster.drivemaster.model.PedidoTienda;

/**
 * Semáforo de los pedidos de tienda para el monitor del SUPERADMIN y los contadores del dashboard.
 *
 * Despacho (pedidos pagados que la tienda aún no despacha ni deja listos para retirar): el plazo es el de siempre,
 * plazoDespachoHasta (PLAZO_DESPACHO_DIAS_HABILES desde el pago, más días si es un encargo). Verde a tiempo,
 * amarillo cuando queda menos del {@value #FRACCION_POR_VENCER_PCT} % del tiempo entre el pago y el plazo, rojo vencido.
 *
 * Retiro (listos para retirar que el cliente no recoge): el mismo criterio que la tienda y RetiroPendienteService.
 * Verde antes de {@value RetiroPendienteService#DIAS_PRIMER_RECORDATORIO} días, amarillo hasta la revisión y rojo desde
 * los {@value RetiroPendienteService#DIAS_REVISION} días (en revisión del ADMIN).
 */
public final class SemaforoPedido {

    public static final String VERDE = "VERDE";
    public static final String AMARILLO = "AMARILLO";
    public static final String ROJO = "ROJO";

    static final int FRACCION_POR_VENCER_PCT = 25;

    private static final List<String> EN_ESPERA_DE_DESPACHO = List.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION);

    private SemaforoPedido() {
    }

    /** Color del despacho, o null si el pedido ya salió de la tienda o no tiene plazo. */
    public static String despacho(PedidoTienda p, LocalDateTime ahora) {
        if (p == null || !EN_ESPERA_DE_DESPACHO.contains(p.getEstado())) return null;
        return despacho(p.getFechaCreacion(), p.getPlazoDespachoHasta(), ahora);
    }

    static String despacho(LocalDateTime pagadoEn, LocalDateTime plazo, LocalDateTime ahora) {
        if (plazo == null) return null;
        if (plazo.isBefore(ahora)) return ROJO;
        if (pagadoEn == null || !pagadoEn.isBefore(plazo)) return VERDE;
        long total = Duration.between(pagadoEn, plazo).toMillis();
        long queda = Duration.between(ahora, plazo).toMillis();
        return queda * 100 < total * FRACCION_POR_VENCER_PCT ? AMARILLO : VERDE;
    }

    /** Color del retiro, o null si el pedido no está esperando que lo recojan. */
    public static String retiro(PedidoTienda p, LocalDateTime ahora) {
        if (p == null || !PedidoTienda.LISTO_PARA_RETIRAR.equals(p.getEstado()) || p.getFechaListoRetiro() == null) return null;
        return retiro(RetiroPendienteService.diasSinRetirar(p, ahora));
    }

    static String retiro(long diasSinRetirar) {
        if (diasSinRetirar >= RetiroPendienteService.DIAS_REVISION) return ROJO;
        if (diasSinRetirar >= RetiroPendienteService.DIAS_PRIMER_RECORDATORIO) return AMARILLO;
        return VERDE;
    }

    // ─── Las mismas reglas como consultas (filtros y contadores) ────────────

    /** Despacho vencido (rojo). */
    public static Criteria despachoVencido(LocalDateTime ahora) {
        return Criteria.where("estado").in(EN_ESPERA_DE_DESPACHO).and("plazoDespachoHasta").lt(ahora);
    }

    /**
     * Despacho por vencer (amarillo): sin vencer, pero lo que queda (plazo − ahora) es menos del 25 % del plazo
     * completo (plazo − pago). La proporción depende de cada pedido, por eso va en $expr.
     */
    public static Criteria despachoPorVencer(LocalDateTime ahora) {
        // Dentro de $expr la fecha va como Date, igual que Spring guarda un LocalDateTime (zona del sistema)
        Date ya = Date.from(ahora.atZone(ZoneId.systemDefault()).toInstant());
        Document queda = new Document("$subtract", List.of("$plazoDespachoHasta", ya));
        Document total = new Document("$subtract", List.of("$plazoDespachoHasta", "$fechaCreacion"));
        Document proporcion = new Document("$lt", List.of(
                new Document("$multiply", List.of(queda, 100)),
                new Document("$multiply", List.of(total, FRACCION_POR_VENCER_PCT))));
        return new Criteria().andOperator(
                Criteria.where("estado").in(EN_ESPERA_DE_DESPACHO).and("plazoDespachoHasta").gte(ahora)
                        .and("fechaCreacion").ne(null),
                expr(proporcion));
    }

    /** {"$expr": documento} tal cual, para combinarlo con otros criterios en un $and. */
    private static Criteria expr(Document expresion) {
        return new Criteria() {
            @Override
            public Document getCriteriaObject() {
                return new Document("$expr", expresion);
            }
        };
    }

    /** Sin retirar: listo para retirar hace {@value RetiroPendienteService#DIAS_PRIMER_RECORDATORIO} días o más (amarillo y rojo). */
    public static Criteria sinRetirar(LocalDateTime ahora) {
        return Criteria.where("estado").is(PedidoTienda.LISTO_PARA_RETIRAR)
                .and("fechaListoRetiro").lte(ahora.minusDays(RetiroPendienteService.DIAS_PRIMER_RECORDATORIO));
    }
}
