package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Panel de la plataforma (solo SUPERADMIN). DriveMaster no vende: lo que pagan los clientes se reparte entre
 * talleres y tiendas, y a la plataforma le corresponde la comisión. Por eso el dinero se separa en "movido" (pasó por
 * la plataforma, no es ingreso suyo) y "comisión registrada" (informativa).
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
     * comisionRegistrada: suma de las líneas informativas de comisión, sin lo anulado por reembolsos (no es dinero cobrado).
     * comisionRetenida: la que sí se descontó a los negocios; comisionSeDescuenta: la bandera. saldosAFavorPendientes: lo
     *               que hay que cobrarles a los negocios a mano por reembolsos de líneas ya pagadas.
     * baseComision: sobre qué se calculó esa comisión: el valor sin IVA ni envío de cada pago desde comisionDesde, sin los
     *               reembolsos (comisionRegistrada = comisionPct % de baseComision).
     * porLiquidar: habilitado y aún sin pagar a los negocios; enEspera: espera la entrega o la recepción del pedido;
     * liquidado: ya pagado (liquidadoSimulado, la parte que pagó la dispersión simulada); liquidacionAutomatica: el modo
     * de dispersión liquida solo al habilitarse la línea (por eso porLiquidar suele quedar en 0).
     */
    public record Dinero(double movido, double comisionRegistrada, double comisionPct, double porLiquidar,
                         double baseComision, LocalDateTime comisionDesde, double enEspera, double liquidado,
                         double liquidadoSimulado, boolean liquidacionAutomatica, ComisionRetenida comisionRetenida,
                         boolean comisionSeDescuenta, double saldosAFavorPendientes) {}

    /**
     * Comisión retenida según el estado de la línea del negocio. cobrada: ya se pagó el neto; en WOMPI, solo con el id del
     * pago. cobradaSimulada: la de líneas SIM-, que no movieron dinero. porCobrar: retenida en líneas aún sin pagar.
     * anulada: la de líneas anuladas por reembolso. sinIdWompi: liquidada en WOMPI sin id: no cuenta hasta que llegue.
     */
    public record ComisionRetenida(double cobrada, double cobradaSimulada, double porCobrar, double anulada, double sinIdWompi) {

        /** Filas de RepartoPagoRepository.comisionRetenidaPorEstado: [estado, modo, 'ID' o vacío, suma]. */
        public static ComisionRetenida de(List<Object[]> filas) {
            double cobrada = 0, simulada = 0, porCobrar = 0, anulada = 0, sinId = 0;
            for (Object[] f : filas) {
                double monto = f[3] instanceof Number n ? n.doubleValue() : 0;
                if ("ANULADO".equals(f[0])) anulada += monto;
                else if (!"LIQUIDADO".equals(f[0])) porCobrar += monto;
                else if ("SIMULADO".equals(f[1])) simulada += monto;
                else if ("WOMPI".equals(f[1]) && !"ID".equals(f[2])) sinId += monto;
                else cobrada += monto;
            }
            return new ComisionRetenida(cobrada, simulada, porCobrar, anulada, sinId);
        }
    }

    public record Alertas(long despachosAtrasados, long problemasEnvioAbiertos, long liquidacionesPendientes,
                          long liquidacionesFallidas, long reembolsosPendientes, double montoReembolsosPendientes,
                          long negociosPorAprobar, long despachosPorVencer, long retirosSinRecoger) {}
}
