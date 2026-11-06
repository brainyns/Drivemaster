package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ComisionGanada;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Comisión ganada" del dashboard: retenida menos anulada por reembolsos, sin la informativa, y siempre igual a la suma de
 * la comisión que muestra Liquidaciones (líneas de negocio no anuladas). Las líneas se arman con ComisionPlataforma,
 * igual que al aprobar un pago y al reembolsarlo, sobre un repositorio en memoria. La base es el precio con IVA, sin
 * envío; las líneas de antes del cambio conservan la comisión que quedó fija.
 */
class ComisionGanadaTest {

    private final List<RepartoPago> lineas = new ArrayList<>();
    private RepartoPagoRepository repartos;
    private PlataformaReportesService reportes;
    private ParametrosMarketplace parametros;
    private long siguienteId = 1;

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);
    private static final LocalDateTime EN_EL_MES = LocalDateTime.of(2026, 11, 10, 15, 0);
    private static final LocalDateTime MES_ANTERIOR = LocalDateTime.of(2026, 10, 28, 9, 0);

    @BeforeEach
    void setUp() {
        repartos = mock(RepartoPagoRepository.class);
        when(repartos.save(any(RepartoPago.class))).thenAnswer(i -> {
            RepartoPago r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(siguienteId++);
                lineas.add(r);
            }
            return r;
        });
        when(repartos.findByPagoId(anyLong())).thenAnswer(i -> delPago(i.getArgument(0)));
        when(repartos.existsByPagoIdAndBeneficiarioTipo(anyLong(), anyString())).thenAnswer(i -> delPago(i.getArgument(0)).stream()
                .anyMatch(r -> r.getBeneficiarioTipo().equals(i.getArgument(1))));
        when(repartos.existsByPagoIdAndBeneficiarioTipoAndReferenciaLiquidacion(anyLong(), anyString(), anyString()))
                .thenAnswer(i -> delPago(i.getArgument(0)).stream().anyMatch(r -> r.getBeneficiarioTipo().equals(i.getArgument(1))
                        && Objects.equals(r.getReferenciaLiquidacion(), i.getArgument(2))));
        when(repartos.sumaComisionDePago(anyLong())).thenAnswer(i -> delPago(i.getArgument(0)).stream()
                .filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).mapToDouble(RepartoPago::getMonto).sum());
        when(repartos.sumaComisionBruta(anyLong())).thenAnswer(i -> delPago(i.getArgument(0)).stream()
                .filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()) && r.getMonto() > 0).mapToDouble(RepartoPago::getMonto).sum());
        when(repartos.comisionEntre(any(), any())).thenAnswer(i -> lineas.stream()
                .filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()) && entre(r.getFechaCreacion(), i.getArgument(0), i.getArgument(1)))
                .toList());
        when(repartos.comisionCobradaEntre(any(), any())).thenAnswer(i -> lineas.stream()
                .filter(r -> !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()) && r.getComisionRetenida() != null && r.getComisionRetenida() > 0
                        && RepartoPago.LIQUIDADO.equals(r.getEstado()) && !"SIMULADO".equals(r.getModoLiquidacion())
                        && entre(r.getFechaLiquidacion(), i.getArgument(0), i.getArgument(1)))
                .toList());
        parametros = mock(ParametrosMarketplace.class);
        when(parametros.comisionRetenida()).thenReturn(true);
        reportes = new PlataformaReportesService(mock(MongoTemplate.class), repartos, mock(ReembolsoRepository.class), parametros);
    }

    private List<RepartoPago> delPago(Long pagoId) {
        return lineas.stream().filter(r -> pagoId.equals(r.getPagoId())).toList();
    }

    private static boolean entre(LocalDateTime f, LocalDateTime desde, LocalDateTime antesDe) {
        return f != null && !f.isBefore(desde) && f.isBefore(antesDe);
    }

    private static PagoEntity pago(long id, double monto) {
        PagoEntity p = new PagoEntity();
        p.setId(id);
        p.setMonto(monto);
        p.setConcepto("CATALOGO");
        p.setReferencia("REF-" + id);
        return p;
    }

    /** Una línea de negocio con su comisión, como la deja hoy la aprobación del pago: base = productos con IVA. */
    private RepartoPago negocio(PagoEntity pago, String pedido, double subtotal, double iva, boolean retener) {
        RepartoPago r = linea(pago, pedido, subtotal + iva);
        ComisionPlataforma.aplicar(r, ComisionPlataforma.baseConIva(subtotal, iva), 10, retener);
        r.setIvaIncluido(iva);
        return repartos.save(r);
    }

    /** Una línea de un pago anterior al cambio: su base era sin IVA y su comisión quedó fija así. */
    private RepartoPago negocioAnterior(PagoEntity pago, String pedido, double bruto, double baseSinIva) {
        RepartoPago r = linea(pago, pedido, bruto);
        ComisionPlataforma.aplicar(r, BigDecimal.valueOf(baseSinIva), 10, true);
        return repartos.save(r);
    }

    private static RepartoPago linea(PagoEntity pago, String pedido, double bruto) {
        return RepartoPago.builder().pagoId(pago.getId()).pedidoTiendaId(pedido).concepto("CATALOGO")
                .beneficiarioTipo(RepartoPago.TIENDA).beneficiarioId("t-" + pedido).monto(bruto).estado(RepartoPago.PENDIENTE)
                .liquidacionPorEvento(true).build();
    }

    /** Los pagos, con la fecha del periodo que les toca (ComisionPlataforma las crea con la hora actual). */
    private void fechar(PagoEntity pago, LocalDateTime fecha) {
        delPago(pago.getId()).forEach(r -> r.setFechaCreacion(fecha));
    }

    @Test
    void laGanadaEsLaRetenidaMenosLasAnulacionesYCuadraConLiquidaciones() {
        // 1. Compra del catálogo de $404.600 + IVA 76.874 con retiro: 481.474 pagados, 48.147 de comisión. Ya liquidada.
        PagoEntity p1 = pago(1, 481_474);
        RepartoPago l1 = negocio(p1, "pedA", 404_600, 76_874, true);
        ComisionPlataforma.registrar(repartos, p1, BigDecimal.valueOf(481_474), 10, true, List.of(l1));
        fechar(p1, EN_EL_MES);
        l1.setEstado(RepartoPago.LIQUIDADO);
        l1.setModoLiquidacion("MANUAL");
        l1.setFechaLiquidacion(EN_EL_MES.plusDays(2));

        // 2. Dos tiendas en un pago; una no despachó: su línea se anula y el reembolso anula su comisión
        PagoEntity p2 = pago(2, 238_000 + 119_000);
        RepartoPago l2a = negocio(p2, "pedB", 200_000, 38_000, true);
        RepartoPago l2b = negocio(p2, "pedC", 100_000, 19_000, true);
        ComisionPlataforma.registrar(repartos, p2, BigDecimal.valueOf(357_000), 10, true, List.of(l2a, l2b));
        l2b.setEstado(RepartoPago.ANULADO);
        ComisionPlataforma.anular(repartos, p2, 119_000, 77L, "pedC");
        fechar(p2, EN_EL_MES);

        // 3. Un pago con el cobro apagado: comisión solo informativa, no cuenta como ganada
        PagoEntity p3 = pago(3, 119_000);
        RepartoPago l3 = negocio(p3, "pedD", 100_000, 19_000, false);
        ComisionPlataforma.registrar(repartos, p3, BigDecimal.valueOf(119_000), 10, false, List.of(l3));
        fechar(p3, EN_EL_MES);

        // 4. Una liquidación simulada no es dinero cobrado
        PagoEntity p4 = pago(4, 119_000);
        RepartoPago l4 = negocio(p4, "pedE", 100_000, 19_000, true);
        ComisionPlataforma.registrar(repartos, p4, BigDecimal.valueOf(119_000), 10, true, List.of(l4));
        fechar(p4, EN_EL_MES);
        l4.setEstado(RepartoPago.LIQUIDADO);
        l4.setModoLiquidacion("SIMULADO");
        l4.setFechaLiquidacion(EN_EL_MES.plusDays(1));

        // 5. Un pago del mes anterior no entra en "Este mes"
        PagoEntity p5 = pago(5, 238_000);
        RepartoPago l5 = negocio(p5, "pedF", 200_000, 38_000, true);
        ComisionPlataforma.registrar(repartos, p5, BigDecimal.valueOf(238_000), 10, true, List.of(l5));
        fechar(p5, MES_ANTERIOR);

        // 6. Un pago registrado antes del cambio: su comisión sobre la base sin IVA (10.000) no se recalcula
        PagoEntity p6 = pago(6, 119_000);
        RepartoPago l6 = negocioAnterior(p6, "pedG", 119_000, 100_000);
        ComisionPlataforma.registrar(repartos, p6, BigDecimal.valueOf(100_000), 10, true, List.of(l6));
        fechar(p6, EN_EL_MES);

        ComisionGanada c = reportes.comisionGanada(RangoFechas.SIN_RANGO, HOY);

        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), c.desde(), "sin fechas es el mes en curso");
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), c.antesDe());
        assertEquals(10_000, l6.getComisionRetenida(), 0.001, "lo ya registrado queda como estaba");
        assertEquals(48_147 + 23_800 + 11_900 + 11_900 + 10_000, c.retenida(), 0.001);
        assertEquals(11_900, c.anulada(), 0.001, "la comisión de la tienda que no despachó se anula");
        assertEquals(48_147 + 23_800 + 11_900 + 10_000, c.ganada(), 0.001);
        assertEquals(11_900, c.informativa(), 0.001, "la informativa va aparte");
        assertEquals(48_147, c.liquidada(), 0.001, "ya liquidada: solo la pagada de verdad, no la simulada");

        // La misma cifra que suma la columna "Comisión" de Liquidaciones: líneas de negocio del periodo, sin las anuladas
        double enLiquidaciones = lineas.stream()
                .filter(r -> !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()) && !RepartoPago.ANULADO.equals(r.getEstado()))
                .filter(r -> entre(r.getFechaCreacion(), c.desde(), c.antesDe()))
                .mapToDouble(r -> r.getComisionRetenida() == null ? 0 : r.getComisionRetenida()).sum();
        assertEquals(enLiquidaciones, c.ganada(), 0.001);
    }

    @Test
    void conUnPeriodoElegidoCuentaSoloEsasFechas() {
        PagoEntity p = pago(1, 238_000);
        RepartoPago l = negocio(p, "pedA", 200_000, 38_000, true);
        ComisionPlataforma.registrar(repartos, p, BigDecimal.valueOf(238_000), 10, true, List.of(l));
        fechar(p, MES_ANTERIOR);

        assertEquals(23_800, reportes.comisionGanada(RangoFechas.de("2026-10-01", "2026-10-31"), HOY).ganada(), 0.001);
        assertEquals(0, reportes.comisionGanada(RangoFechas.de("2026-11-01", "2026-11-30"), HOY).ganada(), 0.001);
    }

    // ─── Las tarjetas de "Dinero" con el mismo periodo ───────────────────────

    /** Las consultas que usan las tarjetas, sobre las mismas líneas en memoria. */
    private void consultasDeDinero(com.drivemaster.drivemaster.repository.mysql.PagoRepository pagos, List<PagoEntity> todos) {
        when(repartos.negocioEntre(any(), any())).thenAnswer(i -> lineas.stream()
                .filter(r -> !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()) && entre(r.getFechaCreacion(), i.getArgument(0), i.getArgument(1)))
                .toList());
        when(repartos.resumenPagado(any(), any())).thenAnswer(i -> {
            Map<String, Double> porModo = new java.util.LinkedHashMap<>();
            lineas.stream().filter(r -> RepartoPago.LIQUIDADO.equals(r.getEstado()) && !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())
                            && entre(r.getFechaLiquidacion(), i.getArgument(0), i.getArgument(1)))
                    .forEach(r -> porModo.merge(Objects.toString(r.getModoLiquidacion(), ""), r.getMonto(), Double::sum));
            List<Object[]> filas = new ArrayList<>();
            porModo.forEach((m, s) -> filas.add(new Object[]{m, 1L, s}));
            return filas;
        });
        when(pagos.sumaMovidaMarketplaceEntre(any(), any())).thenAnswer(i -> todos.stream()
                .filter(p -> entre(p.getFechaPago(), i.getArgument(0), i.getArgument(1))).mapToDouble(PagoEntity::getMonto).sum());
        reportes.setPagoRepository(pagos);
        when(parametros.comisionPct()).thenReturn(10.0);
    }

    @Test
    void con_el_mismo_periodo_las_tarjetas_de_dinero_cuadran_con_la_comision_ganada() {
        var pagosRepo = mock(com.drivemaster.drivemaster.repository.mysql.PagoRepository.class);
        List<PagoEntity> todos = new ArrayList<>();
        // Este mes: una compra liquidada (simulada), una en espera y una reembolsada; el mes pasado, otra que no cuenta
        PagoEntity p1 = pago(1, 238_000 + 8_000);
        p1.setFechaPago(EN_EL_MES);
        RepartoPago l1 = negocio(p1, "pedA", 200_000, 38_000, true);
        ComisionPlataforma.registrar(repartos, p1, BigDecimal.valueOf(238_000), 10, true, List.of(l1));
        fechar(p1, EN_EL_MES);
        l1.setEstado(RepartoPago.LIQUIDADO);
        l1.setModoLiquidacion("SIMULADO");
        l1.setFechaLiquidacion(EN_EL_MES.plusDays(1));

        PagoEntity p2 = pago(2, 119_000);
        p2.setFechaPago(EN_EL_MES);
        RepartoPago l2 = negocio(p2, "pedB", 100_000, 19_000, true);
        ComisionPlataforma.registrar(repartos, p2, BigDecimal.valueOf(119_000), 10, true, List.of(l2));
        fechar(p2, EN_EL_MES);

        PagoEntity p3 = pago(3, 119_000);
        p3.setFechaPago(EN_EL_MES);
        RepartoPago l3 = negocio(p3, "pedC", 100_000, 19_000, true);
        ComisionPlataforma.registrar(repartos, p3, BigDecimal.valueOf(119_000), 10, true, List.of(l3));
        l3.setEstado(RepartoPago.ANULADO);
        ComisionPlataforma.anular(repartos, p3, 119_000, 9L, "pedC");
        fechar(p3, EN_EL_MES);

        PagoEntity p4 = pago(4, 595_000);
        p4.setFechaPago(MES_ANTERIOR);
        RepartoPago l4 = negocio(p4, "pedD", 500_000, 95_000, true);
        ComisionPlataforma.registrar(repartos, p4, BigDecimal.valueOf(595_000), 10, true, List.of(l4));
        fechar(p4, MES_ANTERIOR);
        todos.addAll(List.of(p1, p2, p3, p4));
        consultasDeDinero(pagosRepo, todos);

        ComisionGanada c = reportes.comisionGanada(RangoFechas.SIN_RANGO, HOY);
        var d = c.dinero();
        assertEquals(238_000 + 119_000, d.baseComision(), 0.001, "productos con IVA de lo no reembolsado del mes");
        assertEquals(d.baseComision() * d.comisionPct() / 100, c.ganada() + c.informativa(), 1.0,
                "comisión ganada = porcentaje de la base, con el mismo periodo");
        assertEquals(11_900, d.comisionPorCobrar(), 0.001, "la retenida que falta cobrar: la de la línea en espera");
        assertEquals(l1.getMonto(), d.pagado(), 0.001);
        assertEquals(d.pagado(), d.pagadoSimulado(), 0.001, "todo lo pagado este mes fue simulado");
        assertEquals(246_000 + 119_000 + 119_000, d.movido(), 0.001, "lo pagado en el mes, con envío");

        // El mes pasado: solo su compra, y sigue cuadrando
        ComisionGanada octubre = reportes.comisionGanada(RangoFechas.de("2026-10-01", "2026-10-31"), HOY);
        assertEquals(595_000, octubre.dinero().baseComision(), 0.001);
        assertEquals(octubre.dinero().baseComision() * 0.10, octubre.ganada() + octubre.informativa(), 1.0);
        assertEquals(595_000, octubre.dinero().movido(), 0.001);
        assertEquals(59_500, octubre.dinero().comisionPorCobrar(), 0.001);
    }

    @Test
    void sin_pagos_en_el_periodo_todo_da_cero() {
        consultasDeDinero(mock(com.drivemaster.drivemaster.repository.mysql.PagoRepository.class), List.of());
        var d = reportes.comisionGanada(RangoFechas.de("2026-09-01", "2026-09-30"), HOY).dinero();
        assertEquals(0, d.baseComision() + d.comisionPorCobrar() + d.pagado() + d.movido(), 0.001);
    }
}
