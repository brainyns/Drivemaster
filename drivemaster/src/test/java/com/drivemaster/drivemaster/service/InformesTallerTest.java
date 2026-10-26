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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        when(reparto.resumenPorConceptoYEstadoDesde(anyString(), anyString(), any())).thenReturn(List.of());
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
        when(reparto.resumenPorConceptoYEstadoDesde(eq(RepartoPago.TALLER), eq("A"), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"COTIZACION", RepartoPago.LIQUIDADO, 2L, 500_000.0}));
        when(reparto.resumenPorConceptoYEstadoDesde(eq(RepartoPago.TALLER), eq("B"), any()))
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
        verify(reparto).resumenPorConceptoYEstadoDesde(eq(RepartoPago.TALLER), eq("B"), any());
    }

    @Test
    void sinTallerNoHayInformes() {
        assertThrows(ResponseStatusException.class, () -> service.informes(" ", 6, HASTA, AHORA));
        assertThrows(ResponseStatusException.class, () -> service.informes(null, 6, HASTA, AHORA));
    }

    @Test
    void losIngresosSeparanLiquidadoPendienteYAnulado() {
        when(reparto.resumenPorConceptoYEstadoDesde(eq(RepartoPago.TALLER), eq("A"), any())).thenReturn(List.<Object[]>of(
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

    /** Evento hecho por un taller (actor TALLER con su id), como los deja TransicionOrdenService. */
    private static EventoOrden eventoTaller(String tallerId, String estado, LocalDateTime fecha) {
        return EventoOrden.builder().estado(estado).fecha(fecha).actorRol(EstadoOrden.TALLER).actorId(tallerId).build();
    }

    @Test
    void elTiempoDeRespuestaVaDeLaAsignacionALaRespuestaDelTaller() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 2, 8, 0);
        // Acepta y agenda a las 2 h (sin pago de diagnóstico pasa directo a EN_REVISION)
        OrdenServicio dosHoras = solicitud("s1", "A", EstadoOrden.EN_REVISION, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base), eventoTaller("A", EstadoOrden.EN_REVISION, base.plusHours(2)));
        // Reasignada: otro taller rechazó primero; cuenta desde la ÚLTIMA asignación hasta la respuesta de A
        OrdenServicio reasignada = solicitud("s2", "A", EstadoOrden.EN_REVISION, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base), eventoTaller("B", EstadoOrden.REASIGNADA, base.plusHours(1)),
                evento(EstadoOrden.ASIGNADA_A_TALLER, base.plusHours(5)), eventoTaller("A", EstadoOrden.EN_REVISION, base.plusHours(9)));
        // Con pago de diagnóstico: acepta a las 3 h y EN_REVISION llega al pagar el cliente; se mide hasta la aceptación
        OrdenServicio conPago = solicitud("s3", "A", EstadoOrden.EN_REVISION, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base), eventoTaller("A", EstadoOrden.ASIGNADA_A_TALLER, base.plusHours(3)),
                evento(EstadoOrden.EN_REVISION, base.plusHours(20)));
        OrdenServicio sinRespuesta = solicitud("s4", "A", EstadoOrden.ASIGNADA_A_TALLER, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(dosHoras, reasignada, conPago, sinRespuesta));

        var t = informes("A").tiempoRespuesta();

        assertEquals(3, t.muestra());
        assertEquals(180.0, t.minutosPromedio());
        assertEquals(180.0, t.minutosMediana());
    }

    @Test
    void unaRespuestaDeMinutosNoSeRedondeaACero() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 2, 8, 0);
        OrdenServicio rapida = solicitud("s1", "A", EstadoOrden.EN_REVISION, base,
                evento(EstadoOrden.ASIGNADA_A_TALLER, base), eventoTaller("A", EstadoOrden.EN_REVISION, base.plusSeconds(150)));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(rapida));

        assertEquals(2.5, informes("A").tiempoRespuesta().minutosPromedio());
    }

    @Test
    void sinSolicitudesNoSeInventanPromediosNiCalificaciones() {
        Informes i = informes("A");

        assertEquals(0, i.tiempoRespuesta().muestra());
        assertNull(i.tiempoRespuesta().minutosPromedio());
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
    void losIngresosSePidenDesdeElPrimerDiaDelPeriodo() {
        Informes i = service.informes("A", 3, HASTA, AHORA);

        LocalDateTime esperado = LocalDate.of(2026, 8, 1).atStartOfDay(InformesTallerService.ZONA)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(LocalDate.of(2026, 8, 1), i.periodoDesde());
        verify(reparto).resumenPorConceptoYEstadoDesde(RepartoPago.TALLER, "A", esperado);
    }

    @Test
    void laCalificacionCuentaPorLaFechaEnQueSeCalificoDentroDelPeriodo() {
        OrdenServicio enero = solicitud("c1", "A", EstadoOrden.ENTREGADA, LocalDateTime.of(2026, 1, 5, 9, 0));
        enero.setCalificacionTaller(2);
        enero.setFechaRecepcionCliente(LocalDateTime.of(2026, 1, 10, 9, 0));
        // Pedida antes del periodo, pero calificada dentro: cuenta
        OrdenServicio julio = solicitud("c2", "A", EstadoOrden.ENTREGADA, LocalDateTime.of(2026, 6, 20, 9, 0));
        julio.setCalificacionTaller(5);
        julio.setFechaRecepcionCliente(LocalDateTime.of(2026, 8, 2, 9, 0));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(enero, julio));

        var tres = service.informes("A", 3, HASTA, AHORA).calificacion();
        var doce = service.informes("A", 12, HASTA, AHORA).calificacion();

        assertEquals(1, tres.total());
        assertEquals(5.0, tres.promedio());
        assertEquals(2, doce.total());
        assertEquals(3.5, doce.promedio());
    }

    @Test
    void lasOrdenesAnterioresSoloCuentanSiSeFinalizaronEnElPeriodo() {
        OrdenServicio dentro = OrdenServicio.builder().id("v1").tallerId("A").estado("FINALIZADA").precioManoObra(70_000.0)
                .fechaCreacion(LocalDateTime.of(2026, 3, 1, 9, 0)).fechaFinalizacion(LocalDateTime.of(2026, 9, 1, 9, 0)).build();
        OrdenServicio fuera = OrdenServicio.builder().id("v2").tallerId("A").estado("FINALIZADA").precioManoObra(50_000.0)
                .fechaCreacion(LocalDateTime.of(2026, 1, 1, 9, 0)).fechaFinalizacion(LocalDateTime.of(2026, 2, 1, 9, 0)).build();
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(dentro, fuera));

        var anteriores = service.informes("A", 3, HASTA, AHORA).ingresos().anteriores();

        assertEquals(1, anteriores.ordenes());
        assertEquals(70_000.0, anteriores.manoDeObra());
    }

    @Test
    void cadaSolicitudCuentaEnUnSoloEstado() {
        LocalDateTime creada = LocalDateTime.of(2026, 10, 1, 9, 0);
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(
                solicitud("s1", "A", EstadoOrden.ENTREGADA, creada,
                        evento(EstadoOrden.ASIGNADA_A_TALLER, creada), evento(EstadoOrden.LISTA, creada.plusDays(1)),
                        evento(EstadoOrden.ENTREGADA, creada.plusDays(2))),
                solicitud("s2", "A", EstadoOrden.LISTA, creada),
                solicitud("s3", "A", EstadoOrden.CANCELADA, creada)));

        var s = informes("A").servicios();

        assertEquals(3, s.total());
        assertEquals(s.total(), s.porEstado().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(1L, s.porEstado().get(EstadoOrden.ENTREGADA));
        assertEquals(1L, s.porEstado().get(EstadoOrden.LISTA));
    }

    /** Fecha en hora de Colombia expresada en la hora del servidor, como se guardan. */
    private static LocalDateTime col(int a, int m, int d, int h) {
        return LocalDateTime.of(a, m, d, h, 0).atZone(InformesTallerService.ZONA)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Test
    void laSerieMensualTieneCadaMesDelPeriodoConCeroDondeNoHuboNada() {
        OrdenServicio entregada = solicitud("s1", "A", EstadoOrden.ENTREGADA, col(2026, 8, 3, 9));
        entregada.setFechaEntrega(col(2026, 10, 2, 10));
        // Sin fechaEntrega guardada: usa el evento ENTREGADA
        OrdenServicio conEvento = solicitud("s2", "A", EstadoOrden.ENTREGADA, col(2026, 10, 1, 9),
                evento(EstadoOrden.ENTREGADA, col(2026, 10, 5, 9)));
        OrdenServicio cotizada = solicitud("s3", "A", EstadoOrden.COTIZADA, col(2026, 10, 6, 9));
        OrdenServicio fuera = solicitud("s4", "A", EstadoOrden.ENTREGADA, col(2026, 6, 1, 9));
        fuera.setFechaEntrega(col(2026, 7, 31, 23));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(entregada, conEvento, cotizada, fuera));
        when(reparto.liquidadoDesde(eq(RepartoPago.TALLER), eq("A"), any())).thenReturn(List.<Object[]>of(
                new Object[]{col(2026, 8, 31, 23), 100_000.0},
                new Object[]{col(2026, 10, 1, 0), 40_000.0},
                new Object[]{col(2026, 10, 7, 12), 60_000.0},
                new Object[]{col(2026, 7, 31, 23), 999_000.0}));

        var serie = service.informes("A", 3, HASTA, AHORA).serieMensual();

        assertEquals(List.of("2026-08", "2026-09", "2026-10"), serie.stream().map(m -> m.mes()).toList());
        assertEquals(100_000.0, serie.get(0).ingresosLiquidados(), "el 31 a las 11 p. m. en Colombia sigue siendo agosto");
        assertEquals(0.0, serie.get(1).ingresosLiquidados());
        assertEquals(0, serie.get(1).entregados());
        assertEquals(0, serie.get(1).solicitudes());
        assertEquals(100_000.0, serie.get(2).ingresosLiquidados());
        assertEquals(1, serie.get(0).solicitudes());
        assertEquals(2, serie.get(2).entregados());
        assertEquals(2, serie.get(2).solicitudes());
    }

    @Test
    void lasSolicitudesDeLaSerieSumanLasDelPeriodo() {
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(
                solicitud("s1", "A", EstadoOrden.COTIZADA, col(2026, 9, 3, 9)),
                solicitud("s2", "A", EstadoOrden.CANCELADA, col(2026, 10, 3, 9)),
                solicitud("s3", "A", EstadoOrden.LISTA, col(2026, 10, 4, 9))));

        Informes i = service.informes("A", 6, HASTA, AHORA);

        assertEquals(6, i.serieMensual().size());
        assertEquals(i.servicios().total(), i.serieMensual().stream().mapToLong(m -> m.solicitudes()).sum());
    }

    @Test
    void laSerieSemanalTieneOchoSemanasDeLunesADomingoHastaLaActual() {
        // AHORA es jueves 8 de octubre de 2026: la semana actual empieza el lunes 5
        when(reparto.liquidadoDesde(eq(RepartoPago.TALLER), eq("A"), any())).thenReturn(List.<Object[]>of(
                new Object[]{col(2026, 10, 5, 0), 30_000.0},
                new Object[]{col(2026, 10, 4, 23), 20_000.0},
                new Object[]{col(2026, 8, 10, 8), 10_000.0},
                new Object[]{col(2026, 8, 9, 8), 5_000.0}));

        var semanas = service.informes("A", 1, HASTA, AHORA).serieSemanal();

        assertEquals(InformesTallerService.SEMANAS, semanas.size());
        assertEquals(LocalDate.of(2026, 8, 17), semanas.get(0).inicio());
        assertEquals(LocalDate.of(2026, 10, 5), semanas.get(7).inicio());
        assertEquals(30_000.0, semanas.get(7).ingresosLiquidados());
        assertEquals(20_000.0, semanas.get(6).ingresosLiquidados(), "el domingo es de la semana anterior");
        assertEquals(0.0, semanas.stream().limit(6).mapToDouble(s -> s.ingresosLiquidados()).sum(),
                "lo de antes de la primera semana no entra");
        // Con 1 mes, lo liquidado se pide desde el lunes de la primera semana (antes que el 1 de octubre)
        verify(reparto).liquidadoDesde(RepartoPago.TALLER, "A", col(2026, 8, 17, 0));
    }

    @Test
    void losMesesSeLimitanYLasSolicitudesViejasNoEntranAlPeriodo() {
        OrdenServicio reciente = solicitud("r", "A", EstadoOrden.COTIZADA, LocalDateTime.of(2026, 10, 3, 9, 0));
        OrdenServicio vieja = solicitud("v", "A", EstadoOrden.COTIZADA, LocalDateTime.of(2025, 1, 3, 9, 0));
        when(ordenes.findByTallerIdOrderByFechaCreacionDesc("A")).thenReturn(List.of(reciente, vieja));

        assertEquals(1, service.informes("A", 6, HASTA, AHORA).servicios().total());
        assertEquals(24, service.informes("A", 999, HASTA, AHORA).mesesConsultados());
        assertEquals(6, service.informes("A", 0, HASTA, AHORA).mesesConsultados());
        verify(reparto, org.mockito.Mockito.atLeastOnce()).resumenPorConceptoYEstadoDesde(eq(RepartoPago.TALLER), eq("A"), any());
    }
}
