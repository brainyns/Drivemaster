package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.util.Dinero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Comisión de la plataforma sobre un pago aprobado, guardada como una línea INFORMATIVA del reparto
 * (beneficiario PLATAFORMA). No se liquida ni se cobra aparte: lo que paga el cliente se reparte entre
 * taller y tiendas, y esta línea solo deja constancia de cuánto sería la comisión con el porcentaje
 * vigente en el momento del pago. Sin pedido ni orden de tienda, para que ninguna liquidación la tome.
 */
final class ComisionPlataforma {

    private static final Logger log = LoggerFactory.getLogger(ComisionPlataforma.class);

    private ComisionPlataforma() {
    }

    /** Base antes de IVA de un pago que ya incluye IVA. */
    static BigDecimal baseSinIva(Double montoConIva, double ivaPct) {
        BigDecimal divisor = BigDecimal.valueOf(100).add(BigDecimal.valueOf(ivaPct));
        return Dinero.pesos(montoConIva).multiply(BigDecimal.valueOf(100)).divide(divisor, 0, RoundingMode.HALF_UP);
    }

    /**
     * Base de la comisión del diagnóstico: siempre el precio base sin IVA. Desde el IVA por taller la solicitud lo trae
     * congelado (tarifaDiagnosticoBase); las anteriores cobraban el parámetro con el IVA global incluido y se le quita.
     */
    static BigDecimal baseDiagnostico(OrdenServicio orden, Double montoPagado, double ivaGlobal) {
        if (orden != null && orden.getTarifaDiagnosticoBase() != null) return Dinero.pesos(orden.getTarifaDiagnosticoBase());
        return baseSinIva(montoPagado, ivaGlobal);
    }

    /**
     * Base de la comisión del pago de una cotización: su subtotal antes de IVA menos la parte sin IVA del diagnóstico ya
     * pagado (esa ya tuvo su comisión). Con dos porcentajes de IVA no sirve quitarle uno solo al monto pagado.
     * El anticipo se lleva a su base con el IVA con que se cobró el diagnóstico: el de la solicitud o, en las
     * anteriores al IVA por taller, el global (como se calculó su comisión).
     * Las cotizaciones anteriores al IVA por taller (un solo ivaPct) siguen con la fórmula de antes.
     */
    static BigDecimal baseCotizacion(Cotizacion c, OrdenServicio orden, Double montoPagado, double ivaGlobal) {
        if (c.getIvaRepuestosPct() == null || c.getIvaManoObraPct() == null) {
            return baseSinIva(montoPagado, c.getIvaPct() != null ? c.getIvaPct() : ivaGlobal);
        }
        double ivaDiagnostico = orden != null && orden.getIvaDiagnosticoPct() != null ? orden.getIvaDiagnosticoPct() : ivaGlobal;
        BigDecimal anticipoBase = baseSinIva(c.getAnticipoDiagnostico() != null ? c.getAnticipoDiagnostico() : 0, ivaDiagnostico);
        return Dinero.pesos(c.getSubtotal()).subtract(anticipoBase).max(BigDecimal.ZERO);
    }

    /** Monto de la comisión en pesos enteros. */
    static BigDecimal monto(BigDecimal base, double pct) {
        return Dinero.pesos(base.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    /**
     * Un reembolso anula la comisión del dinero devuelto, con una línea informativa NEGATIVA (la original no se
     * toca, queda el rastro). Reembolso total: toda la comisión que quede. Parcial (la parte de una tienda): la
     * misma proporción que el reembolso tiene del pago. Una sola vez por reembolso y nunca lanza: un fallo aquí no
     * puede romper el reembolso.
     *
     * @param reembolsoId id del reembolso guardado (null en pruebas); pedidoTiendaId distingue el reembolso parcial
     */
    static void anular(RepartoPagoRepository repartos, PagoEntity pago, double montoReembolso, Long reembolsoId,
                       String pedidoTiendaId) {
        try {
            if (pago == null || pago.getId() == null) return;
            double vigente = repartos.sumaComisionDePago(pago.getId());
            if (vigente <= 0) return;
            String referencia = "REEMBOLSO-" + (reembolsoId != null ? reembolsoId
                    : pedidoTiendaId != null ? "PEDIDO-" + pedidoTiendaId : "TOTAL");
            if (repartos.existsByPagoIdAndBeneficiarioTipoAndReferenciaLiquidacion(pago.getId(), RepartoPago.PLATAFORMA, referencia)) return;

            BigDecimal restante = Dinero.pesos(vigente);
            double montoPago = pago.getMonto() != null ? pago.getMonto() : 0;
            BigDecimal anulado;
            if (montoPago <= 0 || montoReembolso >= montoPago - 0.5) {
                anulado = restante;
            } else {
                BigDecimal bruta = Dinero.pesos(repartos.sumaComisionBruta(pago.getId()));
                anulado = Dinero.pesos(bruta.multiply(BigDecimal.valueOf(montoReembolso))
                        .divide(BigDecimal.valueOf(montoPago), 2, RoundingMode.HALF_UP)).min(restante);
            }
            if (anulado.signum() <= 0) return;
            repartos.save(RepartoPago.builder()
                    .pagoId(pago.getId())
                    .ordenServicioId(pago.getOrdenServicioId())
                    .cotizacionId(pago.getCotizacionId())
                    .compraCatalogoId(pago.getCompraCatalogoId())
                    .concepto(pago.getConcepto())
                    .beneficiarioTipo(RepartoPago.PLATAFORMA)
                    .beneficiarioId(RepartoPago.PLATAFORMA)
                    .beneficiarioNombre("DriveMaster")
                    .monto(-Dinero.aDouble(anulado))
                    .estado(RepartoPago.INFORMATIVA)
                    .liquidacionPorEvento(false)
                    .referenciaLiquidacion(referencia)
                    .nota("Comisión anulada por reembolso de $" + Dinero.pesos(montoReembolso).toPlainString())
                    .fechaCreacion(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.error("No se pudo anular la comisión del pago {} por el reembolso", pago != null ? pago.getReferencia() : null, e);
        }
    }

    /**
     * Registra la comisión de un pago aprobado. Idempotente por pago y nunca lanza: un fallo aquí no
     * puede deshacer un pago ya aplicado.
     */
    static void registrar(RepartoPagoRepository repartos, PagoEntity pago, BigDecimal base, double pct) {
        try {
            if (pago == null || pago.getId() == null || base == null || base.signum() <= 0 || pct <= 0) return;
            if (repartos.existsByPagoIdAndBeneficiarioTipo(pago.getId(), RepartoPago.PLATAFORMA)) return;
            BigDecimal comision = monto(base, pct);
            if (comision.signum() <= 0) return;
            repartos.save(RepartoPago.builder()
                    .pagoId(pago.getId())
                    .ordenServicioId(pago.getOrdenServicioId())
                    .cotizacionId(pago.getCotizacionId())
                    .compraCatalogoId(pago.getCompraCatalogoId())
                    .concepto(pago.getConcepto())
                    .beneficiarioTipo(RepartoPago.PLATAFORMA)
                    .beneficiarioId(RepartoPago.PLATAFORMA)
                    .beneficiarioNombre("DriveMaster")
                    .monto(Dinero.aDouble(comision))
                    .estado(RepartoPago.INFORMATIVA)
                    .liquidacionPorEvento(false)
                    .nota("Comisión informativa " + pct + "% sobre $" + base.toPlainString() + " (antes de IVA)")
                    .fechaCreacion(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la comisión del pago {}", pago != null ? pago.getReferencia() : null, e);
        }
    }
}
