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
import com.drivemaster.drivemaster.model.mysql.ResolucionReparto;
import com.drivemaster.drivemaster.repository.mysql.ResolucionRepartoRepository;
import com.drivemaster.drivemaster.service.dispersion.DispersionException;
import com.drivemaster.drivemaster.service.dispersion.DispersionInciertaException;
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

import org.mockito.ArgumentCaptor;

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
        when(repartoRepository.findFirstByReferenciaLiquidacionStartingWith(anyString())).thenAnswer(i -> {
            String ref = i.getArgument(0);
            return repartos.values().stream().filter(r -> r.getReferenciaLiquidacion() != null
                    && r.getReferenciaLiquidacion().startsWith(ref)).findFirst();
        });
        when(repartoRepository.findFirstByReferenciaLiquidacionEndingWith(anyString())).thenAnswer(i -> {
            String sufijo = i.getArgument(0);
            return repartos.values().stream().filter(r -> r.getReferenciaLiquidacion() != null
                    && r.getReferenciaLiquidacion().endsWith(sufijo)).findFirst();
        });
        when(repartoRepository.marcarPorVerificar(anyLong(), anyString(), anyString(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (!RepartoPago.PROCESANDO.equals(r.getEstado())) return 0;
                r.setReferenciaLiquidacion(i.getArgument(1));
                r.setMotivoFallo(i.getArgument(2));
                r.setModoLiquidacion(i.getArgument(3));
                return 1;
            }
        });
        when(repartoRepository.marcarFallidoAsincrono(anyLong(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (r == null || !List.of(RepartoPago.PROCESANDO, RepartoPago.LIQUIDADO).contains(r.getEstado())) return 0;
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
        resoluciones = mock(ResolucionRepartoRepository.class);
        when(repartoRepository.confirmarPagoTardio(anyLong(), any(), anyString(), anyString())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                if (!RepartoPago.FALLIDO.equals(r.getEstado())) return 0;
                r.setEstado(RepartoPago.LIQUIDADO);
                return 1;
            }
        });
        when(repartoRepository.tomarParaLiquidar(anyLong(), any())).thenAnswer(i -> {
            synchronized (repartos) {
                RepartoPago r = repartos.get((Long) i.getArgument(0));
                boolean tomable = RepartoPago.POR_LIQUIDAR.equals(r.getEstado()) || RepartoPago.FALLIDO.equals(r.getEstado());
                if (!tomable || reembolsoRepository.hayPendienteQueAfecta(r.getPagoId(), r.getPedidoTiendaId())) return 0;
                r.setEstado(RepartoPago.PROCESANDO);
                r.setFechaProcesando(i.getArgument(1));
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

    private ResolucionRepartoRepository resoluciones;

    private LiquidacionService servicio(DispersionService dispersion) {
        DispersionService contada = new DispersionService() {
            @Override public String modo() { return dispersion.modo(); }
            @Override public boolean automatica() { return dispersion.automatica(); }
            @Override public boolean dineroReal() { return dispersion.dineroReal(); }
            @Override public void validar(SolicitudDispersion s) { dispersion.validar(s); }
            @Override public ResultadoDispersion liquidar(RepartoPago r, SolicitudDispersion s) {
                dispersiones.incrementAndGet();
                return dispersion.liquidar(r, s);
            }
        };
        return new LiquidacionService(repartoRepository, reembolsoRepository, pagoRepository, contada, avisos, resoluciones);
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

    // ─── Las líneas de la Fase 3 tampoco se pagan sin recepción o entrega ────

    @Test
    void lasLineasDeLaFase3NoSeTocanConElEvento() {
        RepartoPago vieja = linea(1, RepartoPago.TIENDA, "ped1", false);
        servicio(simulada()).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");
        assertEquals(RepartoPago.PENDIENTE, vieja.getEstado());
        assertEquals(0, dispersiones.get());
    }

    @Test
    void unaLineaPendienteAntiguaNoSeLiquidaYElMensajeDiceQueFalta() {
        for (Boolean regla : new Boolean[] { null, false, true }) {
            RepartoPago pendiente = linea(1, RepartoPago.TIENDA, "ped1", true);
            pendiente.setLiquidacionPorEvento(regla);

            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> servicio(manual()).liquidar(1L, ADMIN, "TRF-0001", null));

            assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
            assertTrue(e.getReason().contains("falta confirmar la recepción"), e.getReason());
            assertEquals(RepartoPago.PENDIENTE, pendiente.getEstado());
        }
        assertEquals(0, dispersiones.get());
    }

    @Test
    void siguenPagandoseLasLineasListasYLosReintentos() {
        RepartoPago lista = linea(1, RepartoPago.TIENDA, "ped1", true);
        lista.setEstado(RepartoPago.POR_LIQUIDAR);
        RepartoPago fallida = linea(2, RepartoPago.TIENDA, "ped2", true);
        fallida.setEstado(RepartoPago.FALLIDO);
        LiquidacionService s = servicio(manual());

        assertEquals(RepartoPago.LIQUIDADO, s.liquidar(1L, ADMIN, "TRF-0001", null).getEstado());
        assertEquals(RepartoPago.LIQUIDADO, s.liquidar(2L, ADMIN, "TRF-0002", null).getEstado());
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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
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

        String tomar = jpql("tomarParaLiquidar", Long.class, LocalDateTime.class);
        assertTrue(tomar.contains("set r.estado = 'PROCESANDO'"), tomar);
        assertTrue(tomar.contains("r.estado in ('POR_LIQUIDAR', 'FALLIDO')"), tomar);
        assertTrue(tomar.contains("r.id = :id and r.estado in ('POR_LIQUIDAR', 'FALLIDO') and not exists"), tomar);
        assertFalse(tomar.contains("r.estado = 'PENDIENTE'"), "una línea PENDIENTE nunca se toma: " + tomar);
        assertTrue(tomar.contains("not exists") && tomar.contains("b.estado = 'PENDIENTE'")
                && tomar.contains("b.pedidoTiendaId is null or b.pedidoTiendaId = r.pedidoTiendaId"), tomar);

        String liquidado = jpql("marcarLiquidado", Long.class, LocalDateTime.class, String.class, String.class, String.class, String.class);
        assertTrue(liquidado.contains("r.estado = 'PROCESANDO'"), liquidado);
        String fallido = jpql("marcarFallido", Long.class, String.class, String.class);
        assertTrue(fallido.contains("r.estado = 'PROCESANDO'"), fallido);
    }

    @Test
    void elModoSeExponeParaLaPantalla() {
        assertEquals(Map.of("modo", "SIMULADO", "automatica", true, "dineroReal", false), servicio(simulada()).modo());
        assertEquals(Map.of("modo", "MANUAL", "automatica", false, "dineroReal", true), servicio(manual()).modo());
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
                .referenciaLiquidacion("WOMPI-99-TEST [ID:pout-99]")
                .build();
        repartos.put(99L, r);

        LiquidacionService service = servicio(manual());

        Map<String, Object> payload = Map.of(
                "event", "transaction.updated",
                "data", Map.of("transaction", Map.of(
                        "id", "txn-1",
                        "payoutId", "pout-99",
                        "status", "FAILED",
                        "failureReason", Map.of("code", "E01", "message", "Cuenta bancaria no existe")
                ))
        );

        service.procesarWebhookPayout(payload);
        service.procesarWebhookPayout(payload); // Wompi reintenta los eventos: una sola alerta

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
        assertEquals("WOMPI-100-TEST", repartos.get(100L).getReferenciaLiquidacion());
    }

    @Test
    void elEventoDeUnIntentoAnteriorNoDeshaceElReintentoPagado() {
        RepartoPago r = linea(101, RepartoPago.TALLER, null, true);
        r.setEstado(RepartoPago.LIQUIDADO);
        r.setReferenciaLiquidacion("WOMPI-101-NUEVA [ID:pout-nuevo]");

        servicio(manual()).procesarWebhookPayout(Map.of("event", "payout.updated", "data", Map.of("payout", Map.of(
                "id", "pout-viejo", "reference", "WOMPI-101-VIEJA", "status", "FAILED"))));

        assertEquals(RepartoPago.LIQUIDADO, r.getEstado(), "tampoco se busca por el id que trae la referencia");
        verifyNoInteractions(avisos);
    }

    @Test
    void enWompiElEventoNoPagaSoloQuedaEsperandoAlAdmin() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        DispersionService aprobada = mock(DispersionService.class);
        when(aprobada.modo()).thenReturn("WOMPI");
        when(aprobada.automatica()).thenReturn(false);

        servicio(aprobada).pedidoRecibido("ped1", "RECEPCION_CONFIRMADA_TALLER");

        assertEquals(RepartoPago.POR_LIQUIDAR, tienda.getEstado());
        assertEquals(0, dispersiones.get());
    }

    @Test
    void unPagoSinConfirmarSigueProcesandoYNoSeReintenta() {
        RepartoPago tienda = linea(1, RepartoPago.TIENDA, "ped1", true);
        tienda.setEstado(RepartoPago.POR_LIQUIDAR);
        DispersionService incierta = mock(DispersionService.class);
        when(incierta.modo()).thenReturn("WOMPI");
        when(incierta.liquidar(any(), any())).thenThrow(new DispersionInciertaException("Wompi no confirmó", "WOMPI-1-AB", null));
        LiquidacionService s = servicio(incierta);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> s.liquidar(1L, ADMIN, null, null));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(RepartoPago.PROCESANDO, tienda.getEstado());
        assertEquals("WOMPI-1-AB", tienda.getReferenciaLiquidacion());
        verify(avisos).alertaAdmin(eq("Liquidación sin confirmar"), contains("WOMPI-1-AB"));

        // Segundo clic: no vuelve a salir hacia Wompi
        assertThrows(ResponseStatusException.class, () -> s.liquidar(1L, ADMIN, null, null));
        assertEquals(1, dispersiones.get());

        // El evento de Wompi lo resuelve por la referencia guardada
        s.procesarWebhookPayout(Map.of("event", "payout.updated", "data", Map.of("payout", Map.of(
                "reference", "WOMPI-1-AB", "status", "TOTAL_PAYMENT"))));
        assertEquals(RepartoPago.LIQUIDADO, tienda.getEstado());
    }

    // ─── Salida manual de una línea PROCESANDO ───────────────────────────────

    private static final String MOTIVO = "Revisado en el panel de Wompi el 31 de octubre";

    private RepartoPago enProceso(long id, String motivoFallo, LocalDateTime tomada) {
        RepartoPago r = linea(id, RepartoPago.TALLER, null, true);
        r.setEstado(RepartoPago.PROCESANDO);
        r.setMotivoFallo(motivoFallo);
        r.setFechaProcesando(tomada);
        r.setReferenciaLiquidacion("WOMPI-" + id + "-AB");
        r.setModoLiquidacion("WOMPI");
        return r;
    }

    private void esperaRechazo(HttpStatus estado, String texto, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(estado, e.getStatusCode());
        assertTrue(e.getReason().contains(texto), e.getReason());
    }

    @Test
    void siSalioQuedaLiquidadaConSuReferenciaYElRegistroDeQuienYPorQue() {
        RepartoPago r = enProceso(1, "Wompi no confirmó el pago", LocalDateTime.now());

        servicio(manual()).resolver(1L, ADMIN, "liquidado", MOTIVO, "PANEL-123");

        assertEquals(RepartoPago.LIQUIDADO, r.getEstado());
        assertEquals("WOMPI-1-AB", r.getReferenciaLiquidacion(), "conserva la de Wompi para reconocer sus eventos");
        assertEquals(ADMIN, r.getLiquidadoPor());
        assertTrue(r.getNota().contains(MOTIVO) && r.getNota().contains("PANEL-123"), r.getNota());
        ArgumentCaptor<ResolucionReparto> registro = ArgumentCaptor.forClass(ResolucionReparto.class);
        verify(resoluciones).save(registro.capture());
        assertEquals(1L, registro.getValue().getRepartoId());
        assertEquals(RepartoPago.LIQUIDADO, registro.getValue().getResultado());
        assertEquals(ADMIN, registro.getValue().getResueltoPor());
        assertEquals(MOTIVO, registro.getValue().getMotivo());
        assertEquals(0, dispersiones.get(), "no sale ningún pago nuevo");
    }

    @Test
    void siNoSalioQuedaFallidaYSePuedeReintentar() {
        RepartoPago r = enProceso(1, "Wompi no confirmó el pago", LocalDateTime.now());
        LiquidacionService s = servicio(manual());

        s.resolver(1L, ADMIN, "FALLIDO", MOTIVO, null);

        assertEquals(RepartoPago.FALLIDO, r.getEstado());
        assertEquals(RepartoPago.LIQUIDADO, s.liquidar(1L, ADMIN, "TRF-0001", null).getEstado());
    }

    @Test
    void unPagoEnCursoNoSeResuelveHastaPasarLos30Minutos() {
        RepartoPago r = enProceso(1, null, LocalDateTime.now().minusMinutes(29));
        LiquidacionService s = servicio(manual());

        esperaRechazo(HttpStatus.CONFLICT, "sigue en curso", () -> s.resolver(1L, ADMIN, "FALLIDO", MOTIVO, null));
        assertEquals(RepartoPago.PROCESANDO, r.getEstado());
        verify(resoluciones, never()).save(any());

        r.setFechaProcesando(LocalDateTime.now().minusMinutes(31));
        s.resolver(1L, ADMIN, "FALLIDO", MOTIVO, null);
        assertEquals(RepartoPago.FALLIDO, r.getEstado());
    }

    @Test
    void unaLineaSinHoraDeTomaCuentaComoColgada() {
        assertTrue(LiquidacionService.sePuedeResolver(enProceso(1, null, null), LocalDateTime.now()));
    }

    @Test
    void solamenteSeResuelveUnaLineaEnProceso() {
        RepartoPago r = enProceso(1, "Wompi no confirmó", LocalDateTime.now());
        r.setEstado(RepartoPago.LIQUIDADO);

        esperaRechazo(HttpStatus.CONFLICT, "en proceso", () -> servicio(manual()).resolver(1L, ADMIN, "FALLIDO", MOTIVO, null));
        verify(resoluciones, never()).save(any());
    }

    @Test
    void validaResultadoMotivoYReferencia() {
        RepartoPago r = enProceso(1, "Wompi no confirmó", LocalDateTime.now());
        LiquidacionService s = servicio(manual());

        esperaRechazo(HttpStatus.BAD_REQUEST, "salió o no salió", () -> s.resolver(1L, ADMIN, "OTRO", MOTIVO, "REF"));
        esperaRechazo(HttpStatus.BAD_REQUEST, "por qué", () -> s.resolver(1L, ADMIN, "FALLIDO", "corto", null));
        esperaRechazo(HttpStatus.BAD_REQUEST, "referencia", () -> s.resolver(1L, ADMIN, "LIQUIDADO", MOTIVO, "  "));
        esperaRechazo(HttpStatus.BAD_REQUEST, "80", () -> s.resolver(1L, ADMIN, "LIQUIDADO", MOTIVO, "x".repeat(81)));
        assertEquals(RepartoPago.PROCESANDO, r.getEstado());
    }

    @Test
    void siOtroCambioLaLineaAlResolverNoQuedaRegistro() {
        enProceso(1, "Wompi no confirmó", LocalDateTime.now());
        doReturn(0).when(repartoRepository).marcarFallido(anyLong(), anyString(), anyString());

        esperaRechazo(HttpStatus.CONFLICT, "cambió", () -> servicio(manual()).resolver(1L, ADMIN, "FALLIDO", MOTIVO, null));
        verify(resoluciones, never()).save(any());
    }

    private static Map<String, Object> pagoConfirmado(String referencia) {
        return Map.of("event", "payout.updated", "data", Map.of("payout", Map.of("reference", referencia, "status", "TOTAL_PAYMENT")));
    }

    @Test
    void siWompiConfirmaLoResueltoComoNoSalidoLaLineaQuedaLiquidadaYAlerta() {
        RepartoPago r = enProceso(1, "Wompi no confirmó", LocalDateTime.now());
        LiquidacionService s = servicio(manual());
        s.resolver(1L, ADMIN, "FALLIDO", MOTIVO, null);
        when(resoluciones.existsByRepartoIdAndResultado(1L, RepartoPago.FALLIDO)).thenReturn(true);

        s.procesarWebhookPayout(pagoConfirmado("WOMPI-1-AB"));

        assertEquals(RepartoPago.LIQUIDADO, r.getEstado());
        verify(avisos).alertaAdmin(eq("Pago confirmado tarde"), contains("no la reintentes"));
    }

    @Test
    void unFallidoDelBancoNoSeReviveConUnEventoFueraDeOrden() {
        RepartoPago r = linea(1, RepartoPago.TALLER, null, true);
        r.setEstado(RepartoPago.FALLIDO);
        r.setReferenciaLiquidacion("WOMPI-1-AB");

        servicio(manual()).procesarWebhookPayout(pagoConfirmado("WOMPI-1-AB"));

        assertEquals(RepartoPago.FALLIDO, r.getEstado());
        verifyNoInteractions(avisos);
    }

    @Test
    void siYaSeReintentoConOtraReferenciaAlertaDePosibleDoblePago() {
        RepartoPago r = linea(1, RepartoPago.TALLER, null, true);
        r.setEstado(RepartoPago.LIQUIDADO);
        r.setReferenciaLiquidacion("WOMPI-1-NUEVA");
        when(resoluciones.existsByResultadoAndReferenciaStartingWith(RepartoPago.FALLIDO, "WOMPI-1-VIEJA")).thenReturn(true);

        servicio(manual()).procesarWebhookPayout(pagoConfirmado("WOMPI-1-VIEJA"));

        assertEquals(RepartoPago.LIQUIDADO, r.getEstado());
        verify(avisos).alertaAdmin(eq("Posible doble pago"), contains("WOMPI-1-VIEJA"));
    }

    @Test
    void elWebhookSinFirmaValidaNoProcesaNada() {
        LiquidacionService s = mock(LiquidacionService.class);
        var controlador = new com.drivemaster.drivemaster.controller.LiquidacionController(s, null,
                new com.drivemaster.drivemaster.service.dispersion.FirmaEventosPayout("", "WOMPI"));
        Map<String, Object> evento = Map.of("event", "payout.updated", "data", Map.of("payout", Map.of(
                "reference", "WOMPI-1-AB", "status", "FAILED")));

        assertEquals(HttpStatus.UNAUTHORIZED, controlador.webhook(evento, "cualquiera").getStatusCode());
        verify(s, never()).procesarWebhookPayout(any());
    }
}
