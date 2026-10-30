package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.dispersion.DispersionException;
import com.drivemaster.drivemaster.service.dispersion.DispersionService;
import com.drivemaster.drivemaster.service.dispersion.ManualDispersionService;
import com.drivemaster.drivemaster.service.dispersion.ResultadoDispersion;
import com.drivemaster.drivemaster.service.dispersion.SimulatedDispersionService;
import com.drivemaster.drivemaster.service.dispersion.SolicitudDispersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regla de liquidación de la 4-B con LiquidacionService real y el reparto en memoria. Los
 * UPDATE condicionales del repositorio se emulan con la misma lógica (atómica) que su
 * JPQL; una prueba aparte comprueba que el JPQL real conserva esas condiciones.
 */
class LiquidacionServiceTest {

    private static final String ADMIN = "admin@demo.com";

    private final Map<Long, RepartoPago> repartos = new LinkedHashMap<>();
    /** Reembolsos pendientes: pagoId -> pedidoTiendaId (null = de todo el pago). */
    private final List<Object[]> reembolsosPendientes = new ArrayList<>();

    private RepartoPagoRepository repartoRepository;
    private ReembolsoRepository reembolsoRepository;
    private PagoRepository pagoRepository;
    private final List<Reembolso> reembolsosCreados = new ArrayList<>();
    private AvisosSolicitudServicio avisos;
    private final AtomicInteger dispersiones = new AtomicInteger();

    @BeforeEach
    void setUp() {
        avisos = mock(AvisosSolicitudServicio.class);
        reembolsoRepository = mock(ReembolsoRepository.class);
        when(reembolsoRepository.save(any(Reembolso.class))).thenAnswer(i -> {
            reembolsosCreados.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(reembolsoRepository.existsByPagoId(anyLong())).thenAnswer(i ->
                reembolsosCreados.stream().anyMatch(r -> r.getPagoId().equals(i.getArgument(0))));
        pagoRepository = mock(PagoRepository.class);
        PagoEntity pago = new PagoEntity();
        pago.setId(10L);
        pago.setReferencia("DRV-DIAG");
        pago.setOrdenServicioId("o1");
        pago.setUsuarioId("u1");
        pago.setTransactionId("txn-1");
        pago.setMonto(50_000.0);
        when(pagoRepository.findById(10L)).thenReturn(Optional.of(pago));
        when(reembolsoRepository.hayPendienteQueAfecta(anyLong(), any())).thenAnswer(i -> {
            Long pagoId = i.getArgument(0);
            String pedido = i.getArgument(1);
            return reembolsosPendientes.stream().anyMatch(r -> r[0].equals(pagoId) && (r[1] == null || r[1].equals(pedido)));
        });

        repartoRepository = mock(RepartoPagoRepository.class);
        when(repartoRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(repartos.get((Long) i.getArgument(0))));
        when(repartoRepository.findByReferenciaLiquidacion(anyString())).thenAnswer(i -> {
            String ref = i.getArgument(0);
            return repartos.values().stream().filter(r -> ref.equals(r.getReferenciaLiquidacion())).findFirst();
        });
        when(repartoRepository.marcarFallidoAsincrono(anyLong(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (r == null) return 0;
                r.setEstado(RepartoPago.FALLIDO);
                r.setMotivoFallo(i.getArgument(1));
                return 1;
            }
        });
        when(repartoRepository.findByPedidoTiendaId(anyString())).thenAnswer(i -> repartos.values().stream()
                .filter(r -> i.getArgument(0).equals(r.getPedidoTiendaId())).toList());
        when(repartoRepository.findByOrdenServicioIdAndBeneficiarioTipo(anyString(), anyString())).thenAnswer(i -> repartos.values().stream()
                .filter(r -> i.getArgument(0).equals(r.getOrdenServicioId()) && i.getArgument(1).equals(r.getBeneficiarioTipo())).toList());

        when(repartoRepository.anularPendiente(anyLong(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (!RepartoPago.PENDIENTE.equals(r.getEstado())) return 0;
                r.setEstado(RepartoPago.ANULADO);
                r.setNota(i.getArgument(1));
                return 1;
            }
        });
        when(repartoRepository.marcarPorLiquidar(anyLong(), any(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (!RepartoPago.PENDIENTE.equals(r.getEstado()) || !Boolean.TRUE.equals(r.getLiquidacionPorEvento())) return 0;
                r.setEstado(RepartoPago.POR_LIQUIDAR);
                r.setFechaListo(i.getArgument(1));
                r.setEventoListo(i.getArgument(2));
                return 1;
            }
        });
        when(repartoRepository.tomarParaLiquidar(anyLong())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                boolean tomable = RepartoPago.POR_LIQUIDAR.equals(r.getEstado()) || RepartoPago.FALLIDO.equals(r.getEstado())
                        || (RepartoPago.PENDIENTE.equals(r.getEstado()) && !Boolean.TRUE.equals(r.getLiquidacionPorEvento()));
                if (!tomable || reembolsoRepository.hayPendienteQueAfecta(r.getPagoId(), r.getPedidoTiendaId())) return 0;
                r.setEstado(RepartoPago.PROCESANDO);
                r.setMotivoFallo(null);
                r.setIntentosLiquidacion((r.getIntentosLiquidacion() != null ? r.getIntentosLiquidacion() : 0) + 1);
                return 1;
            }
        });
        when(repartoRepository.marcarLiquidado(anyLong(), any(), anyString(), anyString(), anyString(), any())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (!RepartoPago.PROCESANDO.equals(r.getEstado())) return 0;
                r.setEstado(RepartoPago.LIQUIDADO);
                r.setFechaLiquidacion(i.getArgument(1));
                r.setModoLiquidacion(i.getArgument(2));
                r.setReferenciaLiquidacion(i.getArgument(3));
                r.setLiquidadoPor(i.getArgument(4));
                r.setNota(i.getArgument(5));
                r.setMotivoFallo(null);
                return 1;
            }
        });
        when(repartoRepository.marcarFallido(anyLong(), anyString(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (!RepartoPago.PROCESANDO.equals(r.getEstado())) return 0;
                r.setEstado(RepartoPago.FALLIDO);
                r.setMotivoFallo(i.getArgument(1));
                r.setModoLiquidacion(i.getArgument(2));
                return 1;
            }
        });
    }

    private LiquidacionService servicio(DispersionService dispersion) {
        DispersionService contada = new DispersionService() {
            @Override public String modo() { return dispersion.modo(); }
            @Override public boolean automatica() { return dispersion.automatica(); }
            @Override public void validar(SolicitudDispersion s) { dispersion.validar(s); }
            @Override public ResultadoDispersion liquidar(RepartoPago r, SolicitudDispersion s) {
                dispersiones.incrementAndGet();
                return dispersion.liquidar(r, s);
            }
        };
        return new LiquidacionService(repartoRepository, reembolsoRepository, pagoRepository, contada, avisos);
    }

    private RepartoPago linea(long id, String tipo, String pedidoId, boolean porEvento) {
        RepartoPago r = RepartoPago.builder().id(id).pagoId(10L).ordenServicioId("o1").pedidoTiendaId(pedidoId)
                .beneficiarioTipo(tipo).beneficiarioId("b" + id).beneficiarioNombre("Beneficiario " + id)
                .monto(71_400.0).estado(RepartoPago.PENDIENTE)
                .liquidacionPorEvento(porEvento ? true : null).fechaCreacion(LocalDateTime.now()).build();
        repartos.put(id, r);
        return r;
    }

    private static DispersionService simulada() {
        return new SimulatedDispersionService();
    }

    private static DispersionService manual() {
        return new ManualDispersionService();
    }

    // ─── Modo SIMULADO: el evento liquida solo ───────────────────────────────

    @Test
    void enSimuladoLaRecepcionLiquidaLaLineaDeLaTienda() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        servicio(simulada()).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        assertEquals(RepartoPago.LIQUIDADO, tienda.getEstado());
        assertEquals("SIMULADO", tienda.getModoLiquidacion());
        assertTrue(tienda.getReferenciaLiquidacion().startsWith("SIM-1-"));
        assertEquals("SISTEMA", tienda.getLiquidadoPor());
        assertNotNull(tienda.getFechaLiquidacion());
        assertEquals("RECEPCION_CONFIRMADA_TALLER", tienda.getEventoListo());
        assertNotNull(tienda.getFechaListo());
    }

    @Test
    void laLineaDelTallerSoloSeLiberaConLaOrdenEntregada() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        RepartoPago taller = linea(2, RepartoPago.TALLER, null, true);
        LiquidacionService s = servicio(simulada());

        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(RepartoPago.LIQUIDADO, tienda.getEstado());
        assertEquals(RepartoPago.PENDIENTE, taller.getEstado(), "recibir repuestos no paga al taller");

        s.ordenEntregada("o1");
        assertEquals(RepartoPago.LIQUIDADO, taller.getEstado());
        assertEquals("ORDEN_ENTREGADA", taller.getEventoListo());
    }

    @Test
    void laEntregaDeLaOrdenNoPagaALasTiendas() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        servicio(simulada()).ordenEntregada("o1");
        assertEquals(RepartoPago.PENDIENTE, tienda.getEstado());
        assertEquals(0, dispersiones.get());
    }

    // ─── Modo MANUAL: el evento NO liquida, la paga el ADMIN ─────────────────

    @Test
    void enManualElEventoSoloDejaLaLineaPorLiquidar() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        servicio(manual()).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        assertEquals(RepartoPago.POR_LIQUIDAR, tienda.getEstado());
        assertNull(tienda.getFechaLiquidacion());
        assertNull(tienda.getReferenciaLiquidacion());
        assertEquals(0, dispersiones.get(), "en manual nunca se liquida sola");
    }

    @Test
    void enManualElAdminDebeAnotarElNumeroDeTransferencia() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        LiquidacionService s = servicio(manual());
        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> s.liquidar(1L, ADMIN, " ", "pago"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals(RepartoPago.POR_LIQUIDAR, tienda.getEstado(), "sin número no se toca la línea");
        assertEquals(0, dispersiones.get());

        RepartoPago r = s.liquidar(1L, ADMIN, "TRF-001-2026", "Bancolombia");
        assertEquals(RepartoPago.LIQUIDADO, r.getEstado());
        assertEquals("MANUAL", r.getModoLiquidacion());
        assertEquals("TRF-001-2026", r.getReferenciaLiquidacion());
        assertEquals(ADMIN, r.getLiquidadoPor());
        assertNotNull(r.getFechaLiquidacion());
        assertTrue(r.getNota().contains("Bancolombia"));
    }

    @Test
    void unaLineaNuevaNoSePuedePagarAntesDelEvento() {
        linea(1, RepartoPago.TIENDA, "ped1", true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio(manual()).liquidar(1L, ADMIN, "TRF-001", null));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(RepartoPago.PENDIENTE, repartos.get(1L).getEstado());
        assertEquals(0, dispersiones.get());
    }

    // ─── Solo líneas nuevas: las de la Fase 3 siguen el flujo anterior ───────

    @Test
    void lasLineasDeLaFase3NoSeTocanConElEvento() {
        RepartoPago vieja = linea(1, RepartoPago.TIENDA, "ped1", false);
        servicio(simulada()).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(RepartoPago.PENDIENTE, vieja.getEstado());
        assertEquals(0, dispersiones.get());
    }

    @Test
    void elAdminSiPuedePagarUnaLineaDeLaFase3DesdePendienteConNumero() {
        linea(1, RepartoPago.TIENDA, "ped1", false);
        RepartoPago r = servicio(manual()).liquidar(1L, ADMIN, "TRF-0001", null);
        assertEquals(RepartoPago.LIQUIDADO, r.getEstado());
        assertEquals("MANUAL", r.getModoLiquidacion());
    }

    // ─── Reembolso pendiente ─────────────────────────────────────────────────

    @Test
    void unaLineaConReembolsoPendienteDeSuPedidoNoSeLiquida() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        reembolsosPendientes.add(new Object[]{10L, "ped1"});
        LiquidacionService s = servicio(simulada());

        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(RepartoPago.POR_LIQUIDAR, tienda.getEstado(), "queda lista pero no se paga");
        assertEquals(0, dispersiones.get());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> s.liquidar(1L, ADMIN, null, null));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("reembolso pendiente"));

        // Resuelto el reembolso, ya se puede
        reembolsosPendientes.clear();
        assertEquals(RepartoPago.LIQUIDADO, s.liquidar(1L, ADMIN, null, null).getEstado());
    }

    @Test
    void unReembolsoDeTodoElPagoBloqueaTambienAlTaller() {
        RepartoPago taller = linea(2, RepartoPago.TALLER, null, true);
        reembolsosPendientes.add(new Object[]{10L, null});
        servicio(simulada()).ordenEntregada("o1");
        assertEquals(RepartoPago.POR_LIQUIDAR, taller.getEstado());
        assertEquals(0, dispersiones.get());
    }

    @Test
    void elReembolsoDeOtroPedidoNoBloqueaALaTienda() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        reembolsosPendientes.add(new Object[]{10L, "ped2"});
        servicio(simulada()).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(RepartoPago.LIQUIDADO, tienda.getEstado());
    }

    // ─── No se liquida dos veces ─────────────────────────────────────────────

    @Test
    void noSeLiquidaDosVeces() {
        linea(1, RepartoPago.TIENDA, "ped1", true);
        LiquidacionService s = servicio(simulada());
        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(1, dispersiones.get());

        // El evento repetido no hace nada
        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        // Y el ADMIN tampoco puede pagarla otra vez
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> s.liquidar(1L, ADMIN, null, null));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(1, dispersiones.get());
    }

    @Test
    void dosAdminsALaVezSoloPaganUnaVez() throws Exception {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        LiquidacionService s = servicio(manual());
        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final int n = i;
            resultados.add(pool.submit(() -> {
                salida.await();
                try {
                    s.liquidar(1L, "admin" + n + "@demo.com", "TRF-" + n + "-2026", null);
                    return true;
                } catch (ResponseStatusException e) {
                    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
                    return false;
                }
            }));
        }
        salida.countDown();
        int exitosos = 0;
        for (Future<Boolean> f : resultados) if (f.get()) exitosos++;
        pool.shutdown();

        assertEquals(1, exitosos, "solo un clic gana");
        assertEquals(1, dispersiones.get());
        assertEquals(RepartoPago.LIQUIDADO, tienda.getEstado());
    }

    @Test
    void eventoYAdminALaVezNoDuplican() throws Exception {
        // En SIMULADO el evento paga solo; un ADMIN que llega a la vez no debe pagarla otra vez
        linea(1, RepartoPago.TIENDA, "ped1", true);
        LiquidacionService s = servicio(simulada());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        Future<?> evento = pool.submit(() -> { salida.await(); s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER"); return null; });
        Future<?> admin = pool.submit(() -> {
            salida.await();
            try { s.liquidar(1L, ADMIN, null, null); } catch (ResponseStatusException ignorada) { /* 409: aún no estaba lista o ya pagada */ }
            return null;
        });
        salida.countDown();
        evento.get();
        admin.get();
        pool.shutdown();
        assertTrue(dispersiones.get() <= 1, "se pagó " + dispersiones.get() + " veces");
        assertNotEquals(RepartoPago.PROCESANDO, repartos.get(1L).getEstado());
    }

    // ─── Fallos ──────────────────────────────────────────────────────────────

    private DispersionService queFalla(AtomicInteger intentosHastaExito) {
        return new DispersionService() {
            @Override public String modo() { return "SIMULADO"; }
            @Override public boolean automatica() { return true; }
            @Override public ResultadoDispersion liquidar(RepartoPago r, SolicitudDispersion s) {
                if (intentosHastaExito.getAndDecrement() > 0) throw new DispersionException("Banco no disponible");
                return new ResultadoDispersion("SIMULADO", "SIM-OK-" + r.getId(), java.math.BigDecimal.valueOf(71_400));
            }
        };
    }

    @Test
    void siFallaQuedaFallidoYSeAlertaAlAdmin() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        servicio(queFalla(new AtomicInteger(1))).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        assertEquals(RepartoPago.FALLIDO, tienda.getEstado());
        assertEquals("Banco no disponible", tienda.getMotivoFallo());
        verify(avisos).alertaAdmin(eq("Liquidación fallida"), contains("Banco no disponible"));
    }

    @Test
    void elAdminPuedeReintentarUnaLineaFallida() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        LiquidacionService s = servicio(queFalla(new AtomicInteger(1)));
        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(RepartoPago.FALLIDO, tienda.getEstado());

        RepartoPago r = s.liquidar(1L, ADMIN, null, null);

        assertEquals(RepartoPago.LIQUIDADO, r.getEstado());
        assertEquals("SIM-OK-1", r.getReferenciaLiquidacion());
        assertEquals(ADMIN, r.getLiquidadoPor());
        assertEquals(2, r.getIntentosLiquidacion());
        assertNull(r.getMotivoFallo());
    }

    @Test
    void siElReintentoFallaDeNuevoElAdminRecibeElError() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        LiquidacionService s = servicio(queFalla(new AtomicInteger(5)));
        s.pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> s.liquidar(1L, ADMIN, null, null));
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        assertEquals(RepartoPago.FALLIDO, tienda.getEstado());
    }

    @Test
    void unFalloInesperadoDelEventoNoRompeLaAccionDelTaller() {
        linea(1, RepartoPago.TIENDA, "ped1", true);
        DispersionService rota = new DispersionService() {
            @Override public String modo() { return "SIMULADO"; }
            @Override public boolean automatica() { return true; }
            @Override public ResultadoDispersion liquidar(RepartoPago r, SolicitudDispersion s) {
                throw new IllegalStateException("bug");
            }
        };
        assertDoesNotThrow(() -> servicio(rota).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER"));
        assertEquals(RepartoPago.FALLIDO, repartos.get(1L).getEstado());
    }

    // ─── El JPQL real conserva las condiciones que emula esta prueba ─────────

    private static String jpql(String metodo, Class<?>... params) throws NoSuchMethodException {
        return RepartoPagoRepository.class.getMethod(metodo, params).getAnnotation(Query.class).value();
    }

    @Test
    void lasConsultasRealesTienenLasCondicionesDeLaRegla() throws Exception {
        String evento = jpql("marcarPorLiquidar", Long.class, LocalDateTime.class, String.class);
        assertTrue(evento.contains("r.estado = 'PENDIENTE'") && evento.contains("liquidacionPorEvento = true"), evento);

        String tomar = jpql("tomarParaLiquidar", Long.class);
        assertTrue(tomar.contains("set r.estado = 'PROCESANDO'"), tomar);
        assertTrue(tomar.contains("r.estado in ('POR_LIQUIDAR', 'FALLIDO')"), tomar);
        assertTrue(tomar.contains("liquidacionPorEvento is null or r.liquidacionPorEvento = false"), tomar);
        assertTrue(tomar.contains("not exists") && tomar.contains("b.estado = 'PENDIENTE'")
                && tomar.contains("b.pedidoTiendaId is null or b.pedidoTiendaId = r.pedidoTiendaId"), tomar);

        String liquidado = jpql("marcarLiquidado", Long.class, LocalDateTime.class, String.class, String.class, String.class, String.class);
        assertTrue(liquidado.contains("r.estado = 'PROCESANDO'"), liquidado);
        String fallido = jpql("marcarFallido", Long.class, String.class, String.class);
        assertTrue(fallido.contains("r.estado = 'PROCESANDO'"), fallido);
    }

    @Test
    void elModoSeExponeParaLaPantalla() {
        assertEquals(Map.of("modo", "SIMULADO", "automatica", true), servicio(simulada()).modo());
        assertEquals(Map.of("modo", "MANUAL", "automatica", false), servicio(manual()).modo());
    }

    // ─── Ajuste: liquidación del diagnóstico según cómo termina la orden ─────

    private RepartoPago lineaDiagnostico(long id) {
        RepartoPago r = linea(id, RepartoPago.TALLER, null, true);
        r.setConcepto("DIAGNOSTICO");
        r.setMonto(50_000.0);
        return r;
    }

    private RepartoPago lineaCotizacionDelTaller(long id) {
        RepartoPago r = linea(id, RepartoPago.TALLER, null, true);
        r.setConcepto("COTIZACION");
        return r;
    }

    @Test
    void siElClienteRechazaLaCotizacionLaLineaDelDiagnosticoQuedaHabilitada() {
        RepartoPago diagnostico = lineaDiagnostico(1);
        RepartoPago tienda = linea(2, RepartoPago.TIENDA, "ped1", true);

        servicio(manual()).ordenCanceladaPorRechazoDeCotizacion("o1");

        assertEquals(RepartoPago.POR_LIQUIDAR, diagnostico.getEstado());
        assertEquals("COTIZACION_RECHAZADA_POR_CLIENTE", diagnostico.getEventoListo());
        assertNotNull(diagnostico.getFechaListo());
        assertEquals(RepartoPago.PENDIENTE, tienda.getEstado(), "no toca otras líneas");
        assertTrue(reembolsosCreados.isEmpty(), "el taller sí trabajó: no hay reembolso");
        assertEquals(0, dispersiones.get(), "en manual la paga el ADMIN");
    }

    @Test
    void enSimuladoElRechazoDeLaCotizacionLiquidaElDiagnosticoDelTaller() {
        RepartoPago diagnostico = lineaDiagnostico(1);
        servicio(simulada()).ordenCanceladaPorRechazoDeCotizacion("o1");
        assertEquals(RepartoPago.LIQUIDADO, diagnostico.getEstado());
        assertEquals("COTIZACION_RECHAZADA_POR_CLIENTE", diagnostico.getEventoListo());
    }

    @Test
    void elRechazoSoloHabilitaElDiagnosticoNoLaLineaDeCotizacionDelTaller() {
        RepartoPago cotizacion = lineaCotizacionDelTaller(1);
        servicio(simulada()).ordenCanceladaPorRechazoDeCotizacion("o1");
        assertEquals(RepartoPago.PENDIENTE, cotizacion.getEstado());
        assertEquals(0, dispersiones.get());
    }

    @Test
    void elDiagnosticoHabilitadoPorElRechazoRespetaUnReembolsoPendiente() {
        RepartoPago diagnostico = lineaDiagnostico(1);
        reembolsosPendientes.add(new Object[]{10L, null});
        servicio(simulada()).ordenCanceladaPorRechazoDeCotizacion("o1");
        assertEquals(RepartoPago.POR_LIQUIDAR, diagnostico.getEstado());
        assertEquals(0, dispersiones.get());
    }

    @Test
    void sinTallerLaLineaDelDiagnosticoSeAnulaYSeReembolsa() {
        RepartoPago diagnostico = lineaDiagnostico(1);

        servicio(simulada()).ordenRechazadaSinTaller("o1");

        assertEquals(RepartoPago.ANULADO, diagnostico.getEstado());
        assertTrue(diagnostico.getNota().contains("Ningún taller"));
        assertEquals(0, dispersiones.get(), "no se liquida");
        assertEquals(1, reembolsosCreados.size());
        Reembolso r = reembolsosCreados.get(0);
        assertEquals(10L, r.getPagoId());
        assertEquals(50_000.0, r.getMonto());
        assertEquals(Reembolso.PENDIENTE, r.getEstado());
        assertEquals("DRV-DIAG", r.getReferenciaPago());
        assertEquals("u1", r.getUsuarioId());
        verify(avisos).alertaAdmin(eq("Reembolso pendiente"), contains("50000"));
    }

    @Test
    void sinTallerNoSeDuplicaUnReembolsoYaRegistrado() {
        lineaDiagnostico(1);
        reembolsosCreados.add(Reembolso.builder().pagoId(10L).monto(50_000.0).estado(Reembolso.PENDIENTE).build());
        servicio(manual()).ordenRechazadaSinTaller("o1");
        assertEquals(1, reembolsosCreados.size());
        assertEquals(RepartoPago.ANULADO, repartos.get(1L).getEstado());
    }

    @Test
    void sinTallerNoTocaUnaLineaYaHabilitadaOLiquidada() {
        RepartoPago habilitada = lineaDiagnostico(1);
        habilitada.setEstado(RepartoPago.POR_LIQUIDAR);
        RepartoPago liquidada = lineaDiagnostico(2);
        liquidada.setEstado(RepartoPago.LIQUIDADO);

        servicio(manual()).ordenRechazadaSinTaller("o1");

        assertEquals(RepartoPago.POR_LIQUIDAR, habilitada.getEstado());
        assertEquals(RepartoPago.LIQUIDADO, liquidada.getEstado());
        assertTrue(reembolsosCreados.isEmpty());
    }

    @Test
    void laSolicitudRechazadaPorFaltaDeTalleresAnulaElDiagnosticoYReembolsa() {
        // De punta a punta con la asignación real: no hay talleres -> RECHAZADA
        RepartoPago diagnostico = lineaDiagnostico(1);
        TallerRepository tallerRepository = mock(TallerRepository.class);
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of());
        ParametrosMarketplace parametros = mock(ParametrosMarketplace.class);
        TransicionOrdenService transiciones = mock(TransicionOrdenService.class);
        OrdenServicio orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .estado(EstadoOrden.REASIGNADA).ciudad("Bogotá").build();
        when(transiciones.transicionar(any(), eq(EstadoOrden.RECHAZADA), any(), anyString(), any(), any())).thenAnswer(i -> {
            orden.setEstado(EstadoOrden.RECHAZADA);
            return orden;
        });
        AsignacionTallerService asignacion = new AsignacionTallerService(tallerRepository, transiciones, parametros,
                avisos, servicio(simulada()));

        OrdenServicio resultado = asignacion.asignarSiguiente(orden);

        assertEquals(EstadoOrden.RECHAZADA, resultado.getEstado());
        assertEquals(RepartoPago.ANULADO, diagnostico.getEstado());
        assertEquals(0, dispersiones.get());
        assertEquals(1, reembolsosCreados.size());
        verify(avisos).sinTallerDisponible(orden);
    }

    @Test
    void webhookPayoutRechazadoMarcaFallidoYAlertaAdmin() {
        RepartoPago r = RepartoPago.builder()
                .id(99L)
                .estado(RepartoPago.LIQUIDADO)
                .beneficiarioNombre("Taller Norte")
                .referenciaLiquidacion("WOMPI-99-TEST")
                .build();
        repartos.put(99L, r);

        LiquidacionService service = servicio(manual());

        Map<String, Object> payload = Map.of(
                "event", "payout.updated",
                "data", Map.of(
                        "reference", "WOMPI-99-TEST",
                        "status", "REJECTED",
                        "failure_reason", "Cuenta bancaria no existe"
                )
        );

        service.procesarWebhookPayout(payload);

        assertEquals(RepartoPago.FALLIDO, repartos.get(99L).getEstado());
        assertTrue(repartos.get(99L).getMotivoFallo().contains("Cuenta bancaria no existe"));
        verify(avisos).alertaAdmin(eq("Liquidación rechazada por banco"), contains("Taller Norte"));
    }

    @Test
    void webhookPayoutAprobadoMarcaLiquidadoSiEstabaProcesando() {
        RepartoPago r = RepartoPago.builder()
                .id(100L)
                .estado(RepartoPago.PROCESANDO)
                .beneficiarioNombre("Tienda Sur")
                .referenciaLiquidacion("WOMPI-100-TEST")
                .build();
        repartos.put(100L, r);

        LiquidacionService service = servicio(manual());

        Map<String, Object> payload = Map.of(
                "event", "payout.updated",
                "data", Map.of(
                        "payout", Map.of(
                                "reference", "WOMPI-100-TEST",
                                "status", "APPROVED"
                        )
                )
        );

        service.procesarWebhookPayout(payload);

        assertEquals(RepartoPago.LIQUIDADO, repartos.get(100L).getEstado());
        assertEquals("WOMPI", repartos.get(100L).getModoLiquidacion());
    }
}
