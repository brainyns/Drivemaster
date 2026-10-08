package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Panel de la plataforma (solo SUPERADMIN). DriveMaster no vende: lo que pagan los clientes se reparte entre
 * talleres y tiendas, y a la plataforma le corresponden la comisión y las suscripciones. Por eso el dinero
 * se separa en "movido" (pasó por la plataforma, no es ingreso suyo) y "comisión registrada" (informativa).
 */
public record DashboardPlataformaDTO(
        LocalDateTime generadoEn,
        Negocios tiendas,
        Negocios talleres,
        Clientes clientes,
        Productos productos,
        List<ProductoVendido> productosMasVendidos,
        List<TiendaVendedora> tiendasMasVendedoras,
        Map<String, Long> ordenesPorEstado,
        Dinero dinero,
        Map<String, Long> suscripcionesPorPlan,
        Alertas alertas) {

    /** Registradas = aprobadas + pendientes + rechazadas; las cuentas anteriores al marketplace cuentan como aprobadas. */
    public record Negocios(long registradas, long aprobadas, long pendientes, long rechazadas) {}

    public record Clientes(long total, long nuevos7Dias, long nuevos30Dias) {}

    public record Productos(long total, long activos, long nuevos7Dias, long nuevos30Dias) {}

    public record ProductoVendido(String productoId, String nombre, String tienda, long unidades, double ventas) {}

    public record TiendaVendedora(String tiendaId, String nombre, long pedidos, double ventas) {}

    /**
     * movido: suma de pagos aprobados del marketplace (no es ingreso de DriveMaster).
     * comisionRegistrada: suma de las líneas informativas de comisión (no es dinero cobrado).
     */
    public record Dinero(double movido, double comisionRegistrada, double comisionPct, double porLiquidar) {}

    public record Alertas(long despachosAtrasados, long problemasEnvioAbiertos, long liquidacionesPendientes,
                          long liquidacionesFallidas, long reembolsosPendientes, double montoReembolsosPendientes,
                          long negociosPorAprobar) {}
}
