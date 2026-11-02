package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Totales de una cotización. Todo en COP redondeado al peso.
 *
 *  - Subtotales antes de IVA por tipo (repuestos / mano de obra).
 *  - Repuestos del inventario del taller aparte: los vende el taller, así que llevan el IVA de su mano de obra.
 *  - IVA por tipo, cada uno con su porcentaje: los repuestos con el parámetro IVA y la mano de obra con el del
 *    taller (0 % si no es responsable, ver IvaTaller). Así el reparto es exacto: tienda = repuestos + su IVA;
 *    taller = mano de obra + su IVA − anticipo.
 *  - Anticipo: tarifa de diagnóstico ya pagada, descontada del total. Nunca supera
 *    la mano de obra con su IVA, para no tocar la parte de la tienda.
 *  - Comisión: porcentaje sobre el subtotal antes de IVA; solo informativa.
 */
public final class CalculadoraCotizacion {

    private CalculadoraCotizacion() {
    }

    public record Totales(double subtotalRepuestos, double subtotalRepuestosTaller, double subtotalManoObra, double subtotal,
                          double ivaRepuestos, double ivaRepuestosTaller, double ivaManoObra, double iva, double total,
                          double anticipo, double totalAPagar, double comision) {
    }

    /** Una línea de IVA del desglose que ve el cliente: "IVA repuestos (19 %)" y su monto. */
    public record LineaIva(String texto, double monto) {
    }

    /**
     * Calcula el subtotal de cada ítem (cantidad × precio, u horas × tarifa en la
     * mano de obra por horas) y los totales de la cotización.
     */
    public static Totales calcular(List<CotizacionItem> items, double ivaRepuestosPct, double ivaManoObraPct,
                                   double anticipoDisponible, double comisionPct) {
        double repuestos = 0;
        double repuestosTaller = 0;
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
            if (CotizacionItem.REPUESTO.equals(item.getTipo()) && item.esDelTaller()) repuestosTaller += sub;
            else if (CotizacionItem.REPUESTO.equals(item.getTipo())) repuestos += sub;
            else manoObra += sub;
        }

        double ivaRepuestos = redondear(repuestos * ivaRepuestosPct / 100.0);
        double ivaManoObra = redondear(manoObra * ivaManoObraPct / 100.0);
        double ivaRepuestosTaller = redondear(repuestosTaller * ivaManoObraPct / 100.0);
        double subtotal = repuestos + repuestosTaller + manoObra;
        double iva = ivaRepuestos + ivaRepuestosTaller + ivaManoObra;
        double total = subtotal + iva;
        double anticipo = Math.min(Math.max(anticipoDisponible, 0), manoObra + ivaManoObra);
        double comision = redondear(subtotal * comisionPct / 100.0);

        return new Totales(repuestos, repuestosTaller, manoObra, subtotal, ivaRepuestos, ivaRepuestosTaller, ivaManoObra, iva, total,
                anticipo, total - anticipo, comision);
    }

    /**
     * Desglose del IVA para el cliente (correo y PDF; el frontend sigue la misma regla en desgloseIva.js). Si los dos
     * porcentajes coinciden, una sola línea "IVA (19 %)" con todo el IVA. Si no, una por tipo, solo de los tipos que
     * tiene la cotización: "IVA repuestos (19 %)" e "IVA mano de obra (0 %)".
     */
    public static List<LineaIva> lineasIva(Cotizacion c) {
        double pctRepuestos = c.pctRepuestos();
        double pctManoObra = c.pctManoObra();
        if (pctRepuestos == pctManoObra) {
            return List.of(new LineaIva("IVA (" + pct(pctRepuestos) + ")", valor(c.getIva())));
        }
        List<LineaIva> lineas = new ArrayList<>();
        double piezas = valor(c.getSubtotalRepuestosTaller());
        // Con piezas del inventario hay repuestos de dos dueños: el rótulo dice de quién es cada IVA
        if (valor(c.getSubtotalRepuestos()) > 0) {
            lineas.add(new LineaIva((piezas > 0 ? "IVA repuestos de tiendas (" : "IVA repuestos (") + pct(pctRepuestos) + ")", valor(c.getIvaRepuestos())));
        }
        if (piezas > 0) {
            lineas.add(new LineaIva("IVA del taller (" + pct(pctManoObra) + ")", valor(c.getIvaManoObra()) + valor(c.getIvaRepuestosTaller())));
        } else if (valor(c.getSubtotalManoObra()) > 0) {
            lineas.add(new LineaIva("IVA mano de obra (" + pct(pctManoObra) + ")", valor(c.getIvaManoObra())));
        }
        return lineas;
    }

    /** "19 %", "0 %", "5,5 %". */
    public static String pct(double pct) {
        return (pct == Math.floor(pct) ? String.valueOf((long) pct) : String.valueOf(pct).replace('.', ',')) + " %";
    }

    private static double valor(Double v) {
        return v != null ? v : 0;
    }

    private static double redondear(double valor) {
        return Math.round(valor);
    }
}
