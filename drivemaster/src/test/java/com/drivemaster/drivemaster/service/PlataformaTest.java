package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Comisión informativa de la plataforma y cifras del panel (con repositorios simulados). */
class PlataformaTest {

    private static PagoEntity pago() {
        PagoEntity p = new PagoEntity();
        p.setId(7L);
        p.setReferencia("DRV-1");
        p.setConcepto("DIAGNOSTICO");
        p.setOrdenServicioId("o1");
        p.setMonto(59_500.0);
        return p;
    }

    // ─── Comisión ────────────────────────────────────────────────────────────

    @Test
    void laBaseSeCalculaAntesDeIvaYLaComisionEnPesosEnteros() {
        assertEquals(new BigDecimal("50000"), ComisionPlataforma.baseSinIva(59_500.0, 19));
        assertEquals(new BigDecimal("5000"), ComisionPlataforma.monto(new BigDecimal("50000"), 10));
        // 50.000 con IVA 19% -> 42.017 de base -> 10% = 4.202 (redondeo a pesos)
        assertEquals(new BigDecimal("42017"), ComisionPlataforma.baseSinIva(50_000.0, 19));
        assertEquals(new BigDecimal("4202"), ComisionPlataforma.monto(new BigDecimal("42017"), 10));
    }

    @Test
    void registraUnaLineaInformativaSinPedidoNiLiquidacion() {
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        ComisionPlataforma.registrar(repartos, pago(), new BigDecimal("50000"), 10);

        ArgumentCaptor<RepartoPago> captor = ArgumentCaptor.forClass(RepartoPago.class);
        verify(repartos).save(captor.capture());
        RepartoPago linea = captor.getValue();
        assertEquals(RepartoPago.PLATAFORMA, linea.getBeneficiarioTipo());
        assertEquals(RepartoPago.INFORMATIVA, linea.getEstado());
        assertEquals(5_000.0, linea.getMonto());
        assertEquals(7L, linea.getPagoId());
        assertEquals("DIAGNOSTICO", linea.getConcepto());
        assertNull(linea.getPedidoTiendaId());
        assertFalse(Boolean.TRUE.equals(linea.getLiquidacionPorEvento()));
        assertTrue(linea.getEstado().length() <= 12, "cabe en la columna estado varchar(12)");
    }

    @Test
    void esIdempotentePorPagoYNoRegistraSinPorcentajeNiBase() {
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        when(repartos.existsByPagoIdAndBeneficiarioTipo(7L, RepartoPago.PLATAFORMA)).thenReturn(true);
        ComisionPlataforma.registrar(repartos, pago(), new BigDecimal("50000"), 10);
        verify(repartos, never()).save(any());

        RepartoPagoRepository otra = mock(RepartoPagoRepository.class);
        ComisionPlataforma.registrar(otra, pago(), new BigDecimal("50000"), 0);
        ComisionPlataforma.registrar(otra, pago(), BigDecimal.ZERO, 10);
        ComisionPlataforma.registrar(otra, pago(), null, 10);
        verify(otra, never()).save(any());
    }

    @Test
    void unFalloAlRegistrarLaComisionNuncaDeshaceElPago() {
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        when(repartos.save(any())).thenThrow(new RuntimeException("base caída"));
        assertDoesNotThrow(() -> ComisionPlataforma.registrar(repartos, pago(), new BigDecimal("50000"), 10));
    }

    // ─── Liquidaciones: la comisión no es algo por pagar ─────────────────────

    @Test
    void laPantallaDeLiquidacionesNoMuestraLaLineaDeComision() {
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        RepartoPago tienda = RepartoPago.builder().id(1L).beneficiarioTipo(RepartoPago.TIENDA).estado(RepartoPago.POR_LIQUIDAR).build();
        RepartoPago comision = RepartoPago.builder().id(2L).beneficiarioTipo(RepartoPago.PLATAFORMA).estado(RepartoPago.INFORMATIVA).build();
        when(repartos.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of(tienda, comision));
        when(repartos.findByEstadoOrderByFechaCreacionDesc(RepartoPago.INFORMATIVA)).thenReturn(List.of(comision));

        LiquidacionService servicio = new LiquidacionService(repartos, mock(ReembolsoRepository.class),
                mock(PagoRepository.class), mock(com.drivemaster.drivemaster.service.dispersion.DispersionService.class),
                mock(AvisosSolicitudServicio.class));

        assertEquals(List.of(tienda), servicio.repartos(null));
        assertTrue(servicio.repartos("informativa").isEmpty());
    }

    // ─── Cifras del panel ────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void elPanelSeparaAprobadasPendientesYRechazadasYElDineroMovidoDeLaComision() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        // count: devuelve según lo que filtra la consulta
        when(mongo.count(any(Query.class), eq(Tienda.class))).thenAnswer(i -> {
            String q = ((Query) i.getArgument(0)).getQueryObject().toJson();
            return q.contains("PENDIENTE") ? 2L : q.contains("RECHAZADO") ? 1L : 6L;
        });
        when(mongo.count(any(Query.class), eq(Taller.class))).thenAnswer(i -> {
            String q = ((Query) i.getArgument(0)).getQueryObject().toJson();
            return q.contains("PENDIENTE") ? 1L : q.contains("RECHAZADO") ? 0L : 4L;
        });
        when(mongo.count(any(Query.class), eq(Producto.class))).thenReturn(9L);
        AggregationResults<Document> vacio = mock(AggregationResults.class);
        when(vacio.getMappedResults()).thenReturn(List.of(new Document("_id", "EN_REVISION").append("n", 3)));
        when(mongo.aggregate(any(Aggregation.class), anyString(), eq(Document.class))).thenReturn(vacio);

        PagoRepository pagos = mock(PagoRepository.class);
        when(pagos.sumaMovidaMarketplace()).thenReturn(1_000_000.0);
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        when(repartos.sumaComisionRegistrada()).thenReturn(80_000.0);
        when(repartos.countByEstado(RepartoPago.POR_LIQUIDAR)).thenReturn(5L);
        ReembolsoRepository reembolsos = mock(ReembolsoRepository.class);
        when(reembolsos.countByEstado("PENDIENTE")).thenReturn(2L);
        when(reembolsos.sumaPendiente()).thenReturn(120_000.0);
        SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
        ParametrosMarketplace parametros = mock(ParametrosMarketplace.class);
        when(parametros.comisionPct()).thenReturn(10.0);

        DashboardPlataformaDTO d = new PlataformaDashboardService(mongo, pagos, repartos, reembolsos, suscripciones, parametros)
                .dashboard(LocalDateTime.of(2026, 10, 7, 12, 0));

        assertEquals(6, d.tiendas().registradas());
        assertEquals(2, d.tiendas().pendientes());
        assertEquals(1, d.tiendas().rechazadas());
        assertEquals(3, d.tiendas().aprobadas());
        assertEquals(4, d.talleres().registradas());
        assertEquals(3, d.talleres().aprobadas());
        assertEquals(3, d.alertas().negociosPorAprobar());
        assertEquals(3L, d.ordenesPorEstado().get("EN_REVISION"));
        assertEquals(1_000_000.0, d.dinero().movido());
        assertEquals(80_000.0, d.dinero().comisionRegistrada());
        assertEquals(10.0, d.dinero().comisionPct());
        assertEquals(5, d.alertas().liquidacionesPendientes());
        assertEquals(2, d.alertas().reembolsosPendientes());
        assertEquals(120_000.0, d.alertas().montoReembolsosPendientes());
        assertEquals(9, d.productos().total());
    }
}
