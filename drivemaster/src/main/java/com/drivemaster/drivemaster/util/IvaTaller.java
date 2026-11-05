package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Taller;

/**
 * IVA según el régimen del taller. Solo afecta lo que cobra el taller (mano de obra y diagnóstico); los repuestos
 * siempre llevan el parámetro IVA global.
 *
 * Responsable de IVA (o sin el dato, como los talleres anteriores al campo): el parámetro IVA. No responsable: 0 %.
 */
public final class IvaTaller {

    private IvaTaller() {
    }

    public static boolean esResponsable(Taller taller) {
        return taller == null || !Boolean.FALSE.equals(taller.getResponsableIva());
    }

    /** Porcentaje de IVA de la mano de obra y el diagnóstico de este taller. */
    public static double pct(Taller taller, double ivaGlobal) {
        return esResponsable(taller) ? ivaGlobal : 0;
    }

    /** IVA de un monto, redondeado al peso. */
    public static double iva(double base, double pct) {
        return Math.round(base * pct / 100.0);
    }

    /** Monto con su IVA: lo que paga el cliente por un precio base (la tarifa de diagnóstico, por ejemplo). */
    public static double conIva(double base, double pct) {
        return base + iva(base, pct);
    }
}
