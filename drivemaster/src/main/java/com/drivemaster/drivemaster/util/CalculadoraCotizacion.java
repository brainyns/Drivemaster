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
     * La frase que explica al cliente cómo se aplicó el adelanto del diagnóstico. anticipo es la parte aplicada
     * (la cotización solo guarda esa); diagnosticoPagado es la tarifa original cuando se conoce (null si no).
     * manoObraConIva = mano de obra con su IVA: el tope del adelanto. Nunca inventa cifras: sin la tarifa,
     * describe la aplicada.
     */
    public static String explicacionAdelanto(double anticipo, Double diagnosticoPagado, double manoObraConIva,
                                             java.util.function.DoubleFunction<String> moneda) {
        if (diagnosticoPagado != null && diagnosticoPagado > anticipo) {
            return "Diagnóstico " + moneda.apply(diagnosticoPagado) + " · se usaron " + moneda.apply(anticipo)
                    + " para pagar la mano de obra.";
        }
        if (anticipo < manoObraConIva) {
            return "Se descontaron los " + moneda.apply(anticipo) + " del diagnóstico.";
        }
        return "Se usaron los " + moneda.apply(anticipo) + " del diagnóstico para pagar la mano de obra.";
    }

    /** Mano de obra con su IVA: el tope hasta donde llega el adelanto. */
    public static double manoObraConIva(Cotizacion c) {
        return valor(c.getSubtotalManoObra()) + valor(c.getIvaManoObra());
    }

    /** IVA de una línea: repuestos con el del tipo que venden (taller o tienda), mano de obra con el del taller. */
    public static double ivaDeLinea(Cotizacion c, CotizacionItem i) {
        double sub = valor(i.getSubtotal());
        double pct = CotizacionItem.REPUESTO.equals(i.getTipo())
                ? (i.esDelTaller() ? c.pctManoObra() : c.pctRepuestos())
                : c.pctManoObra();
        return redondear(sub * pct / 100.0);
    }

    /** Total de una línea con su IVA (lo que ve el cliente por línea). */
    public static double lineaConIva(Cotizacion c, CotizacionItem i) {
        return valor(i.getSubtotal()) + ivaDeLinea(c, i);
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


    /** Precio por unidad de un repuesto como lo paga el cliente: base antes de IVA, su IVA y el total. */
    public record PrecioFinal(double base, double ivaPct, double iva, double total) {
        public boolean sinIva() {
            return ivaPct == 0;
        }
    }

    /**
     * Si la cotización mezcla dos IVA (taller no responsable o con otro porcentaje), el mismo repuesto se ve con bases
     * distintas según quién lo vende: la línea aclara su precio final por unidad. null si no es repuesto o no hace falta.
     * Mismo redondeo al peso que los totales; el frontend sigue la misma regla en desgloseIva.js.
     */
    public static PrecioFinal precioFinal(Cotizacion c, CotizacionItem i) {
        if (!CotizacionItem.REPUESTO.equals(i.getTipo()) || i.getPrecioUnitario() == null) return null;
        if (c.pctRepuestos() == c.pctManoObra()) return null;
        double pct = i.esDelTaller() ? c.pctManoObra() : c.pctRepuestos();
        double base = i.getPrecioUnitario();
        double iva = redondear(base * pct / 100.0);
        return new PrecioFinal(base, pct, iva, base + iva);
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
