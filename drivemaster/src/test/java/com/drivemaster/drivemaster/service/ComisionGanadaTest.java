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
 * igual que al aprobar un pago y al reembolsarlo, sobre un repositorio en memoria.
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

    /** Una línea de negocio con su comisión, como la deja la aprobación del pago. */
    private RepartoPago negocio(PagoEntity pago, String pedido, double bruto, double base, boolean retener) {
        RepartoPago r = RepartoPago.builder().pagoId(pago.getId()).pedidoTiendaId(pedido).concepto("CATALOGO")
                .beneficiarioTipo(RepartoPago.TIENDA).beneficiarioId("t-" + pedido).monto(bruto).estado(RepartoPago.PENDIENTE)
                .liquidacionPorEvento(true).build();
        ComisionPlataforma.aplicar(r, BigDecimal.valueOf(base), 10, retener);
        return repartos.save(r);
    }

    /** Los pagos, con la fecha del periodo que les toca (ComisionPlataforma las crea con la hora actual). */
    private void fechar(PagoEntity pago, LocalDateTime fecha) {
        delPago(pago.getId()).forEach(r -> r.setFechaCreacion(fecha));
    }

    @Test
    void laGanadaEsLaRetenidaMenosLasAnulacionesYCuadraConLiquidaciones() {
        // 1. Compra del catálogo de $404.600 sin IVA con retiro: 481.474 pagados, 40.460 de comisión. Ya liquidada.
        PagoEntity p1 = pago(1, 481_474);
        RepartoPago l1 = negocio(p1, "pedA", 481_474, 404_600, true);
        ComisionPlataforma.registrar(repartos, p1, BigDecimal.valueOf(404_600), 10, true, List.of(l1));
        fechar(p1, EN_EL_MES);
        l1.setEstado(RepartoPago.LIQUIDADO);
        l1.setModoLiquidacion("MANUAL");
        l1.setFechaLiquidacion(EN_EL_MES.plusDays(2));

        // 2. Dos tiendas en un pago; una no despachó: su línea se anula y el reembolso anula su comisión
        PagoEntity p2 = pago(2, 238_000 + 119_000);
        RepartoPago l2a = negocio(p2, "pedB", 238_000, 200_000, true);
        RepartoPago l2b = negocio(p2, "pedC", 119_000, 100_000, true);
        ComisionPlataforma.registrar(repartos, p2, BigDecimal.valueOf(300_000), 10, true, List.of(l2a, l2b));
        l2b.setEstado(RepartoPago.ANULADO);
        ComisionPlataforma.anular(repartos, p2, 119_000, 77L, "pedC");
        fechar(p2, EN_EL_MES);

        // 3. Un pago con el cobro apagado: comisión solo informativa, no cuenta como ganada
        PagoEntity p3 = pago(3, 119_000);
        RepartoPago l3 = negocio(p3, "pedD", 119_000, 100_000, false);
        ComisionPlataforma.registrar(repartos, p3, BigDecimal.valueOf(100_000), 10, false, List.of(l3));
        fechar(p3, EN_EL_MES);

        // 4. Una liquidación simulada no es dinero cobrado
        PagoEntity p4 = pago(4, 119_000);
        RepartoPago l4 = negocio(p4, "pedE", 119_000, 100_000, true);
        ComisionPlataforma.registrar(repartos, p4, BigDecimal.valueOf(100_000), 10, true, List.of(l4));
        fechar(p4, EN_EL_MES);
        l4.setEstado(RepartoPago.LIQUIDADO);
        l4.setModoLiquidacion("SIMULADO");
        l4.setFechaLiquidacion(EN_EL_MES.plusDays(1));

        // 5. Un pago del mes anterior no entra en "Este mes"
        PagoEntity p5 = pago(5, 238_000);
        RepartoPago l5 = negocio(p5, "pedF", 238_000, 200_000, true);
        ComisionPlataforma.registrar(repartos, p5, BigDecimal.valueOf(200_000), 10, true, List.of(l5));
        fechar(p5, MES_ANTERIOR);

        ComisionGanada c = reportes.comisionGanada(RangoFechas.SIN_RANGO, HOY);

        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), c.desde(), "sin fechas es el mes en curso");
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), c.antesDe());
        assertEquals(40_460 + 20_000 + 10_000 + 10_000, c.retenida(), 0.001);
        assertEquals(10_000, c.anulada(), 0.001, "la comisión de la tienda que no despachó se anula");
        assertEquals(40_460 + 20_000 + 10_000, c.ganada(), 0.001);
        assertEquals(10_000, c.informativa(), 0.001, "la informativa va aparte");
        assertEquals(40_460, c.liquidada(), 0.001, "ya liquidada: solo la pagada de verdad, no la simulada");

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
        RepartoPago l = negocio(p, "pedA", 238_000, 200_000, true);
        ComisionPlataforma.registrar(repartos, p, BigDecimal.valueOf(200_000), 10, true, List.of(l));
        fechar(p, MES_ANTERIOR);

        assertEquals(20_000, reportes.comisionGanada(RangoFechas.de("2026-10-01", "2026-10-31"), HOY).ganada(), 0.001);
        assertEquals(0, reportes.comisionGanada(RangoFechas.de("2026-11-01", "2026-11-30"), HOY).ganada(), 0.001);
    }
}
