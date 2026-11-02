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
import java.util.List;

/**
 * Comisión de la plataforma sobre un pago aprobado. Cada línea de un taller o tienda guarda su desglose (aplicar): con
 * la bandera COMISION_RETENIDA_ENABLED encendida, su monto queda en el neto que se le transfiere; apagada, recibe todo y
 * la comisión solo queda calculada. Lo que se fija al aprobar el pago ya no cambia si la bandera cambia después.
 *
 * Además, cada pago tiene una línea PLATAFORMA con el total: INFORMATIVA si no se descontó y RETENIDA si sí. No se
 * liquida: no tiene pedido ni orden de tienda, para que ninguna liquidación la tome.
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
     * Fija el desglose de una línea que todavía tiene el bruto en monto. La comisión nunca supera el bruto, así que el
     * neto nunca es negativo. Sin retener, el monto no cambia: el negocio recibe todo.
     */
    static void aplicar(RepartoPago linea, BigDecimal base, double pct, boolean retener) {
        BigDecimal bruto = Dinero.pesos(linea.getMonto());
        BigDecimal baseLinea = base == null ? BigDecimal.ZERO : base.max(BigDecimal.ZERO);
        BigDecimal calculada = monto(baseLinea, pct).min(bruto).max(BigDecimal.ZERO);
        BigDecimal retenida = retener ? calculada : BigDecimal.ZERO;
        linea.setMontoBruto(linea.getMonto());
        linea.setComisionBase(Dinero.aDouble(baseLinea));
        linea.setComisionPct(pct);
        linea.setComisionCalculada(Dinero.aDouble(calculada));
        linea.setComisionRetenida(Dinero.aDouble(retenida));
        if (retenida.signum() > 0) linea.setMonto(Dinero.aDouble(bruto.subtract(retenida)));
    }

    private static BigDecimal retenida(RepartoPago r) {
        return r.getComisionRetenida() == null ? BigDecimal.ZERO : Dinero.pesos(r.getComisionRetenida());
    }

    private static BigDecimal suma(List<RepartoPago> lineas, java.util.function.Function<RepartoPago, BigDecimal> valor) {
        return lineas.stream().map(valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean esDeUnNegocio(RepartoPago r) {
        return !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo());
    }

    /**
     * Lo que queda a favor de DriveMaster si se devuelve dinero de líneas que ya se pagaron: el neto transferido por ese
     * dinero, que hay que cobrarle al negocio a mano. pedidoTiendaId limita el cálculo a su línea; sin él cuenta todo el
     * pago. Nunca lanza: un fallo aquí no puede romper el reembolso.
     */
    static double saldoAFavor(RepartoPagoRepository repartos, PagoEntity pago, String pedidoTiendaId, double montoReembolso) {
        try {
            if (pago == null || pago.getId() == null) return 0;
            List<RepartoPago> lineas = repartos.findByPagoId(pago.getId()).stream().filter(ComisionPlataforma::esDeUnNegocio)
                    .filter(r -> pedidoTiendaId == null || pedidoTiendaId.equals(r.getPedidoTiendaId())).toList();
            BigDecimal transferido = suma(lineas.stream().filter(r -> RepartoPago.LIQUIDADO.equals(r.getEstado())).toList(),
                    r -> Dinero.pesos(r.getMonto()));
            if (transferido.signum() <= 0) return 0;
            BigDecimal alcance = pedidoTiendaId != null
                    ? suma(lineas, r -> Dinero.pesos(r.getMontoBruto() != null ? r.getMontoBruto() : r.getMonto()))
                    : Dinero.pesos(pago.getMonto());
            if (alcance.signum() <= 0 || BigDecimal.valueOf(montoReembolso).compareTo(alcance) >= 0) {
                return Dinero.aDouble(transferido);
            }
            return Dinero.aDouble(Dinero.pesos(transferido.multiply(BigDecimal.valueOf(montoReembolso))
                    .divide(alcance, 2, RoundingMode.HALF_UP)));
        } catch (RuntimeException e) {
            log.error("No se pudo calcular el saldo a favor del pago {}", pago != null ? pago.getReferencia() : null, e);
            return 0;
        }
    }

    /**
     * Un reembolso anula la comisión del dinero devuelto, con una línea NEGATIVA (la original no se toca, queda el
     * rastro). Si la comisión de la línea se retuvo, se anula exactamente la suya: una proporción del dinero no cuadra
     * cuando cada parte lleva otro IVA. Una línea que no quedó anulada (ya se pagó) conserva su comisión: esa diferencia
     * es el saldoAFavor del reembolso. Sin retención sigue la regla anterior: reembolso total, toda la comisión que
     * quede; parcial (la parte de una tienda), la misma proporción que el reembolso tiene del pago. Una sola vez por
     * reembolso y nunca lanza: un fallo aquí no puede romper el reembolso.
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

            List<RepartoPago> lineas = repartos.findByPagoId(pago.getId());
            BigDecimal anulado = aAnular(repartos, pago, lineas, montoReembolso, pedidoTiendaId, Dinero.pesos(vigente));
            if (anulado.signum() <= 0) return;
            boolean retenida = lineas.stream().anyMatch(r -> RepartoPago.RETENIDA.equals(r.getEstado()));
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
                    .estado(retenida ? RepartoPago.RETENIDA : RepartoPago.INFORMATIVA)
                    .liquidacionPorEvento(false)
                    .referenciaLiquidacion(referencia)
                    .nota("Comisión anulada por reembolso de $" + Dinero.pesos(montoReembolso).toPlainString())
                    .fechaCreacion(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.error("No se pudo anular la comisión del pago {} por el reembolso", pago != null ? pago.getReferencia() : null, e);
        }
    }

    private static BigDecimal aAnular(RepartoPagoRepository repartos, PagoEntity pago, List<RepartoPago> lineas,
                                      double montoReembolso, String pedidoTiendaId, BigDecimal restante) {
        List<RepartoPago> delPedido = pedidoTiendaId == null ? List.of() : lineas.stream()
                .filter(r -> pedidoTiendaId.equals(r.getPedidoTiendaId()) && retenida(r).signum() > 0).toList();
        if (!delPedido.isEmpty()) {
            return suma(delPedido.stream().filter(r -> RepartoPago.ANULADO.equals(r.getEstado())).toList(),
                    ComisionPlataforma::retenida).min(restante);
        }
        double montoPago = pago.getMonto() != null ? pago.getMonto() : 0;
        // Con retención cada línea sabe su comisión: lo que queda en las vivas es lo único que sigue contando
        boolean conRetencion = pedidoTiendaId == null && lineas.stream().anyMatch(r -> esDeUnNegocio(r) && retenida(r).signum() > 0);
        if (montoPago <= 0 || montoReembolso >= montoPago - 0.5 || conRetencion) {
            BigDecimal enLineasVivas = suma(lineas.stream()
                    .filter(r -> esDeUnNegocio(r) && !RepartoPago.ANULADO.equals(r.getEstado())).toList(), ComisionPlataforma::retenida);
            return restante.subtract(enLineasVivas).max(BigDecimal.ZERO);
        }
        BigDecimal bruta = Dinero.pesos(repartos.sumaComisionBruta(pago.getId()));
        return Dinero.pesos(bruta.multiply(BigDecimal.valueOf(montoReembolso))
                .divide(BigDecimal.valueOf(montoPago), 2, RoundingMode.HALF_UP)).min(restante);
    }

    /**
     * La línea PLATAFORMA de un pago aprobado: con retención, la suma de lo descontado en sus líneas, para que el total
     * siempre cuadre con ellas; sin retención, la comisión sobre la base de todo el pago, como siempre.
     */
    static void registrar(RepartoPagoRepository repartos, PagoEntity pago, BigDecimal base, double pct, boolean retener,
                          List<RepartoPago> lineas) {
        if (!retener) {
            registrar(repartos, pago, base, pct);
            return;
        }
        try {
            if (pago == null || pago.getId() == null) return;
            if (repartos.existsByPagoIdAndBeneficiarioTipo(pago.getId(), RepartoPago.PLATAFORMA)) return;
            BigDecimal comision = suma(lineas, ComisionPlataforma::retenida);
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
                    .estado(RepartoPago.RETENIDA)
                    .liquidacionPorEvento(false)
                    .nota("Comisión retenida " + pct + "% sobre $" + suma(lineas, r -> Dinero.pesos(r.getComisionBase())).toPlainString()
                            + " (antes de IVA)")
                    .fechaCreacion(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la comisión retenida del pago {}", pago != null ? pago.getReferencia() : null, e);
        }
    }

    /**
     * Registra la comisión informativa de un pago aprobado. Idempotente por pago y nunca lanza: un fallo aquí no
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
