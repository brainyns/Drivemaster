package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Informes;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Informes del taller: cálculos, separación entre flujos y aislamiento entre talleres. */
class InformesTallerTest {

    private static final YearMonth HASTA = YearMonth.of(2026, 10);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 8, 12, 0);

    private OrdenServicioRepository ordenes;
    private RepartoPagoRepository reparto;
    private InformesTallerService service;

    @BeforeEach
    void setUp() {
        ordenes = mock(OrdenServicioRepository.class);
        reparto = mock(RepartoPagoRepository.class);
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc(anyString())).thenReturn(List.of());
        when(reparto.resumenPorConceptoYEstado(anyString(), anyString())).thenReturn(List.of());
        service = new InformesTallerService(ordenes, reparto);
    }

    private static EventoOrden evento(String estado, LocalDateTime fecha) {
        return EventoOrden.builder().estado(estado).fecha(fecha).build();
    }

    private static OrdenServicio solicitud(String id, String tallerId, String estado, LocalDateTime creada, EventoOrden... eventos) {
        return OrdenServicio.builder().id(id).tallerId(tallerId).flujo(EstadoOrden.FLUJO_MARKETPLACE).estado(estado)
                .fechaCreacion(creada).eventos(new ArrayList<>(List.of(eventos))).build();
    }

    private Informes informes(String tallerId) {
        return service.informes(tallerId, 6, HASTA, AHORA);
    }

    @Test
    void cadaTallerSoloVeSusPropiosDatos() {
        LocalDateTime creada = LocalDateTime.of(2026, 10, 1, 9, 0);
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A"))
                .thenReturn(List.of(solicitud("a1", "A", EstadoOrden.ENTREGADA, creada), solicitud("a2", "A", EstadoOrden.ENTREGADA, creada)));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("B"))
                .thenReturn(List.of(solicitud("b1", "B", EstadoOrden.CANCELADA, creada)));
        when(reparto.resumenPorConceptoYEstado(RepartoPago.TALLER, "A"))
                .thenReturn(List.<Object[]>of(new Object[]{"COTIZACION", RepartoPago.LIQUIDADO, 2L, 500_000.0}));
        when(reparto.resumenPorConceptoYEstado(RepartoPago.TALLER, "B"))
                .thenReturn(List.<Object[]>of(new Object[]{"COTIZACION", RepartoPago.LIQUIDADO, 1L, 90_000.0}));

        Informes a = informes("A");
        Informes b = informes("B");

        assertEquals(2, a.servicios().total());
        assertEquals(2L, a.servicios().porEstado().get(EstadoOrden.ENTREGADA));
        assertEquals(500_000.0, a.ingresos().manoDeObra().liquidado());
        assertEquals(1, b.servicios().total());
        assertEquals(1L, b.servicios().porEstado().get(EstadoOrden.CANCELADA));
        assertEquals(0L, b.servicios().porEstado().get(EstadoOrden.ENTREGADA));
        assertEquals(90_000.0, b.ingresos().manoDeObra().liquidado());
        verify(ordenes).findByTallerIdOrderByFechaCreacionDesc("A");
        verify(reparto).resumenPorConceptoYEstado(RepartoPago.TALLER, "B");
    }

    @Test
    void sinTallerNoHayInformes() {
        assertThrows(ResponseStatusException.class, () -> service.informes(" ", 6, HASTA, AHORA));
        assertThrows(ResponseStatusException.class, () -> service.informes(null, 6, HASTA, AHORA));
    }

    @Test
    void losIngresosSeparanLiquidadoPendienteYAnulado() {
        when(reparto.resumenPorConceptoYEstado(RepartoPago.TALLER, "A")).thenReturn(List.<Object[]>of(
                new Object[]{"COTIZACION", RepartoPago.LIQUIDADO, 3L, 300_000.0},
                new Object[]{"COTIZACION", RepartoPago.PENDIENTE, 1L, 80_000.0},
                new Object[]{"COTIZACION", RepartoPago.POR_LIQUIDAR, 1L, 20_000.0},
                new Object[]{"COTIZACION", RepartoPago.ANULADO, 1L, 40_000.0},
                new Object[]{"DIAGNOSTICO", RepartoPago.LIQUIDADO, 2L, 100_000.0},
                new Object[]{"DIAGNOSTICO", RepartoPago.FALLIDO, 1L, 50_000.0}));

        var ing = informes("A").ingresos();

        assertEquals(300_000.0, ing.manoDeObra().liquidado());
        assertEquals(3, ing.manoDeObra().lineasLiquidadas());
        assertEquals(100_000.0, ing.manoDeObra().pendiente());
        assertEquals(2, ing.manoDeObra().lineasPendientes());
        assertEquals(100_000.0, ing.diagnostico().liquidado());
        assertEquals(50_000.0, ing.diagnostico().pendiente());
        assertEquals(40_000.0, ing.anulado());
    }

    @Test
    void lasOrdenesDelFlujoAnteriorVanAparteYNoMezclanLosIngresos() {
        OrdenServicio vieja = OrdenServicio.builder().id("v1").tallerId("A").estado("FINALIZADA").precioManoObra(70_000.0)
                .fechaCreacion(LocalDateTime.of(2026, 9, 1, 9, 0)).build();
        OrdenServicio viejaPagada = OrdenServicio.builder().id("v2").tallerId("A").estado("PAGADA").precioManoObra(50_000.0).build();
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(vieja, viejaPagada));

        Informes i = informes("A");

        assertEquals(1, i.ingresos().anteriores().ordenes());
        assertEquals(70_000.0, i.ingresos().anteriores().manoDeObra());
        assertEquals(0, i.servicios().total(), "las órdenes anteriores no cuentan como servicios del marketplace");
        assertEquals(0.0, i.ingresos().manoDeObra().liquidado());
    }

    @Test
    void elTiempoDeRespuestaSaleDeLaLineaDeTiempo() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 2, 8, 0);
        OrdenServicio dosHoras = solicitud("s1", "A", EstadoOrden.EN_REVISION, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base), evento(EstadoOrden.EN_REVISION, base.plusHours(2)));
        // Reasignada: cuenta desde la ÚLTIMA asignación anterior a la revisión
        OrdenServicio reasignada = solicitud("s2", "A", EstadoOrden.EN_REVISION, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base), evento(EstadoOrden.ASIGNADA_A_TALLER, base.plusHours(5)),
                evento(EstadoOrden.EN_REVISION, base.plusHours(9)));
        OrdenServicio sinRevision = solicitud("s3", "A", EstadoOrden.ASIGNADA_A_TALLER, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(dosHoras, reasignada, sinRevision));

        var t = informes("A").tiempoRespuesta();

        assertEquals(2, t.muestra());
        assertEquals(3.0, t.horasPromedio());
        assertEquals(3.0, t.horasMediana());
    }

    @Test
    void sinSolicitudesNoSeInventanPromediosNiCalificaciones() {
        Informes i = informes("A");

        assertEquals(0, i.tiempoRespuesta().muestra());
        assertNull(i.tiempoRespuesta().horasPromedio());
        assertEquals(0, i.calificacion().total());
        assertNull(i.calificacion().promedio());
        assertEquals(0, i.calificacion().recientes().size());
    }

    @Test
    void laCalificacionPromediaSoloLoQueElClienteCalifico() {
        LocalDateTime creada = LocalDateTime.of(2026, 9, 1, 9, 0);
        OrdenServicio cinco = solicitud("c1", "A", EstadoOrden.ENTREGADA, creada);
        cinco.setCalificacionTaller(5);
        cinco.setComentarioCalificacion("Excelente trabajo");
        OrdenServicio tres = solicitud("c2", "A", EstadoOrden.ENTREGADA, creada.plusDays(1));
        tres.setCalificacionTaller(3);
        OrdenServicio sin = solicitud("c3", "A", EstadoOrden.ENTREGADA, creada.plusDays(2));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(cinco, tres, sin));

        var c = informes("A").calificacion();

        assertEquals(2, c.total());
        assertEquals(4.0, c.promedio());
        assertEquals(1L, c.distribucion().get(5));
        assertEquals(1L, c.distribucion().get(3));
        assertEquals(1, c.recientes().size(), "solo se muestran los que dejaron comentario");
    }

    @Test
    void losMesesSeLimitanYLasSolicitudesViejasNoEntranAlPeriodo() {
        OrdenServicio reciente = solicitud("r", "A", EstadoOrden.COTIZADA, LocalDateTime.of(2026, 10, 3, 9, 0));
        OrdenServicio vieja = solicitud("v", "A", EstadoOrden.COTIZADA, LocalDateTime.of(2025, 1, 3, 9, 0));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(reciente, vieja));

        assertEquals(1, service.informes("A", 6, HASTA, AHORA).servicios().total());
        assertEquals(24, service.informes("A", 999, HASTA, AHORA).mesesConsultados());
        assertEquals(6, service.informes("A", 0, HASTA, AHORA).mesesConsultados());
        verify(reparto, org.mockito.Mockito.atLeastOnce()).resumenPorConceptoYEstado(eq(RepartoPago.TALLER), eq("A"));
    }
}
