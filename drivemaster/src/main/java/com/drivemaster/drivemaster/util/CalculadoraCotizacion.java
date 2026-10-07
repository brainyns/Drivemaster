package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.CotizacionItem;

import java.util.List;

/**
 * Totales de una cotización. Todo en COP redondeado al peso.
 *
 *  - Subtotales antes de IVA por tipo (repuestos / mano de obra).
 *  - IVA (porcentaje del parámetro IVA) calculado por tipo, para que el reparto
 *    sea exacto: tienda = repuestos + su IVA; taller = mano de obra + su IVA − anticipo.
 *  - Anticipo: tarifa de diagnóstico ya pagada, descontada del total. Nunca supera
 *    la mano de obra con su IVA, para no tocar la parte de la tienda.
 *  - Comisión: porcentaje sobre el subtotal antes de IVA; solo informativa.
 */
public final class CalculadoraCotizacion {

    private CalculadoraCotizacion() {
    }

    public record Totales(double subtotalRepuestos, double subtotalManoObra, double subtotal,
                          double ivaRepuestos, double ivaManoObra, double iva, double total,
                          double anticipo, double totalAPagar, double comision) {
    }

    /**
     * Calcula el subtotal de cada ítem (cantidad × precio, u horas × tarifa en la
     * mano de obra por horas) y los totales de la cotización.
     */
    public static Totales calcular(List<CotizacionItem> items, double ivaPct,
                                   double anticipoDisponible, double comisionPct) {
        double repuestos = 0;
        double manoObra = 0;
        for (CotizacionItem item : items) {
            double precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : 0;
            double sub;
            if (CotizacionItem.MANO_OBRA.equals(item.getTipo()) && CotizacionItem.POR_HORAS.equals(item.getModalidad())) {
                sub = redondear((item.getHoras() != null ? item.getHoras() : 0) * precio);
            } else {
                sub = redondear((item.getCantidad() != null ? item.getCantidad() : 0) * precio);
            }
            item.setSubtotal(sub);
            if (CotizacionItem.REPUESTO.equals(item.getTipo())) repuestos += sub;
            else manoObra += sub;
        }

        double ivaRepuestos = redondear(repuestos * ivaPct / 100.0);
        double ivaManoObra = redondear(manoObra * ivaPct / 100.0);
        double subtotal = repuestos + manoObra;
        double iva = ivaRepuestos + ivaManoObra;
        double total = subtotal + iva;
        double anticipo = Math.min(Math.max(anticipoDisponible, 0), manoObra + ivaManoObra);
        double comision = redondear(subtotal * comisionPct / 100.0);

        return new Totales(repuestos, manoObra, subtotal, ivaRepuestos, ivaManoObra, iva, total,
                anticipo, total - anticipo, comision);
    }

    private static double redondear(double valor) {
        return Math.round(valor);
    }
}
