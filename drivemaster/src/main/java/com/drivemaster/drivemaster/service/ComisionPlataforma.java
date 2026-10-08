package com.drivemaster.drivemaster.service;

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

    /** Monto de la comisión en pesos enteros. */
    static BigDecimal monto(BigDecimal base, double pct) {
        return Dinero.pesos(base.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
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
