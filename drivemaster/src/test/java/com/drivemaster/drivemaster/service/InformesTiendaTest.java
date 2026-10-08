package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Informes;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Informes de la tienda: solo lo suyo (aislamiento A/B), mes en hora de Colombia y cálculos. */
class InformesTiendaTest {

    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 7, 12, 0);

    private MongoTemplate mongo;
    private RepartoPagoRepository repartos;
    private InformesTiendaService servicio;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class, invocation -> {
            Class<?> tipo = invocation.getMethod().getReturnType();
            if (tipo == AggregationResults.class) return new AggregationResults<Document>(List.of(), new Document());
            if (tipo == long.class) return 0L;
            if (tipo == List.class) return new ArrayList<>();
            return RETURNS_DEFAULTS.answer(invocation);
        });
        repartos = mock(RepartoPagoRepository.class);
        servicio = new InformesTiendaService(mongo, repartos);
    }

    @SuppressWarnings("unchecked")
    private static AggregationResults<Document> filas(Document... d) {
        return new AggregationResults<>(List.of(d), new Document());
    }

    // ─── Aislamiento: la tienda A nunca consulta datos de la B ───────────────

    @Test
    void todaConsultaDeLaTiendaAFiltraPorAYNuncaTocaLaB() {
        servicio.informes("A", 6, OCT_2026, AHORA);
        servicio.informes("B", 6, OCT_2026, AHORA);

        // Se separan las invocaciones: la primera mitad es de A y la segunda de B
        List<Invocation> llamadas = new ArrayList<>(mockingDetails(mongo).getInvocations());
        int mitad = llamadas.size() / 2;
        assertEquals(llamadas.size() % 2, 0, "ambas pasadas hacen las mismas consultas");
        for (int i = 0; i < llamadas.size(); i++) {
            String esperada = i < mitad ? "tiendaId=A" : "tiendaId=B";
            String prohibida = i < mitad ? "tiendaId=B" : "tiendaId=A";
            String consulta = textoDeConsulta(llamadas.get(i));
            assertTrue(consulta.contains(esperada), "la consulta " + llamadas.get(i).getMethod().getName() + " no filtra por la tienda: " + consulta);
            assertFalse(consulta.contains(prohibida), "la consulta toca la otra tienda: " + consulta);
        }
        // Y el dinero pendiente, solo del dueño
        verify(repartos).resumenPorEstado(RepartoPago.TIENDA, "A");
        verify(repartos).resumenPorEstado(RepartoPago.TIENDA, "B");
        verify(repartos, times(2)).resumenPorEstado(anyString(), anyString());
    }

    private static String textoDeConsulta(Invocation llamada) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : llamada.getArguments()) {
            if (arg instanceof Query q) sb.append(q.getQueryObject());
            if (arg instanceof Aggregation a) a.toPipeline(Aggregation.DEFAULT_CONTEXT).forEach(sb::append);
        }
        return sb.toString();
    }

    @Test
    void sinTiendaNoSeConsultaNada() {
        for (String sinTienda : new String[]{null, "", "  "}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.informes(sinTienda, 6, OCT_2026, AHORA));
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
        assertTrue(mockingDetails(mongo).getInvocations().isEmpty());
        verifyNoInteractions(repartos);
    }

    @Test
    void elInformeNoLlevaDatosDeClientes() {
        for (var tipo : com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.class.getDeclaredClasses()) {
            for (var campo : tipo.getRecordComponents()) {
                String n = campo.getName().toLowerCase();
                assertFalse(n.contains("cliente") || n.contains("telefono") || n.contains("direccion") || n.contains("correo"),
                        tipo.getSimpleName() + "." + campo.getName());
            }
        }
    }

    // ─── Mes en hora de Colombia ─────────────────────────────────────────────

    @Test
    void elMesSeAgrupaEnHoraDeColombiaYElPeriodoArrancaALas5UTCDelDia1() {
        servicio.informes("A", 1, OCT_2026, AHORA);

        Aggregation ventas = mockingDetails(mongo).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("aggregate") && i.getArguments()[0] instanceof Aggregation a
                        && a.toPipeline(Aggregation.DEFAULT_CONTEXT).toString().contains("dateToString"))
                .map(i -> (Aggregation) i.getArguments()[0]).findFirst().orElseThrow();
        List<Document> pipeline = ventas.toPipeline(Aggregation.DEFAULT_CONTEXT);
        Document mes = (Document) ((Document) ((Document) pipeline.stream().filter(d -> d.containsKey("$project")).findFirst().orElseThrow()
                .get("$project")).get("mes")).get("$dateToString");
        assertEquals("America/Bogota", mes.getString("timezone"));
        assertEquals("%Y-%m", mes.getString("format"));

        // Una compra a las 11 p. m. del 31 de octubre (Colombia) cae en octubre, no en noviembre
        java.time.ZoneId zona = java.time.ZoneId.of(mes.getString("timezone"));
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        assertEquals("2026-10", f.format(Instant.parse("2026-11-01T04:00:00Z").atZone(zona)));
        assertEquals("2026-11", f.format(Instant.parse("2026-11-01T05:00:00Z").atZone(zona)));

        Document filtro = (Document) pipeline.stream().filter(d -> d.containsKey("$match")).findFirst().orElseThrow().get("$match");
        java.util.Date gte = (java.util.Date) ((Document) filtro.get("fechaCreacion")).get("$gte");
        assertEquals(Instant.parse("2026-10-01T05:00:00Z"), gte.toInstant());
    }

    // ─── Cálculos ────────────────────────────────────────────────────────────

    @Test
    void ventasPorMesAlineaLosMesesYRellenaLosVacios() {
        AggregationResults<Document> ventas = filas(
                new Document("_id", "2026-08").append("pedidos", 2).append("ventas", 120_000.0),
                new Document("_id", "2026-10").append("pedidos", 5).append("ventas", 480_000.0));
        when(mongo.aggregate(any(Aggregation.class), eq("pedidos_tienda"), eq(Document.class))).thenReturn(ventas, new AggregationResults<>(List.of(), new Document()));

        Informes r = servicio.informes("A", 3, OCT_2026, AHORA);

        assertEquals(List.of("2026-08", "2026-09", "2026-10"), r.ventas().meses().stream().map(m -> m.mes()).toList());
        assertEquals(List.of(120_000.0, 0.0, 480_000.0), r.ventas().meses().stream().map(m -> m.ventas()).toList());
        assertEquals(7, r.ventas().pedidos());
        assertEquals(600_000.0, r.ventas().ventas());
    }

    @Test
    void stockBajoListaLosQueEstanEnOBajoSuMinimoLosMasUrgentesPrimero() {
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(
                producto("p1", "Filtro", 2, 5),     // bajo
                producto("p2", "Aceite", 10, 5),    // bien
                producto("p3", "Bujía", 0, 4),      // agotado: el más urgente
                producto("p4", "Pastilla", 5, null),// mínimo por defecto 5: en el límite
                producto("p5", "Sin stock cargado", null, 5)));

        var bajo = servicio.informes("A", 6, OCT_2026, AHORA).stockBajo();

        assertEquals(3, bajo.total());
        assertEquals(List.of("p3", "p1", "p4"), bajo.items().stream().map(p -> p.productoId()).toList());
        assertEquals(5, bajo.items().get(2).minimo());
    }

    private static Producto producto(String id, String nombre, Integer stock, Integer minimo) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCodigo("C-" + id);
        p.setStockActual(stock);
        p.setStockMinimo(minimo);
        return p;
    }

    @Test
    void elTiempoDeDespachoPromediaHorasYCuentaCuantosFueronATiempo() {
        PedidoTienda aTiempo = PedidoTienda.builder().estado(PedidoTienda.DESPACHADO)
                .fechaCreacion(AHORA.minusDays(5)).fechaDespacho(AHORA.minusDays(5).plusHours(10))
                .plazoDespachoHasta(AHORA.minusDays(3)).build();
        PedidoTienda tarde = PedidoTienda.builder().estado(PedidoTienda.RETIRADO)
                .fechaCreacion(AHORA.minusDays(4)).fechaListoRetiro(AHORA.minusDays(4).plusHours(50))
                .plazoDespachoHasta(AHORA.minusDays(3)).build();
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of(aTiempo, tarde));
        when(mongo.count(any(Query.class), eq(PedidoTienda.class))).thenReturn(3L, 1L);

        var d = servicio.informes("A", 6, OCT_2026, AHORA).despacho();

        assertEquals(2, d.despachados());
        assertEquals(30.0, d.horasPromedio());
        assertEquals(50.0, d.porcentajeATiempo());
        assertEquals(3, d.pendientes());
        assertEquals(1, d.atrasados());
    }

    @Test
    void sinDespachosElPromedioEsNuloNoCero() {
        var d = servicio.informes("A", 6, OCT_2026, AHORA).despacho();
        assertEquals(0, d.despachados());
        assertNull(d.horasPromedio());
        assertNull(d.porcentajeATiempo());
    }

    @Test
    void loPendienteDeLiquidarSeSeparaPorEstado() {
        when(repartos.resumenPorEstado(RepartoPago.TIENDA, "A")).thenReturn(List.of(
                new Object[]{RepartoPago.PENDIENTE, 2L, 300_000.0},
                new Object[]{RepartoPago.PROCESANDO, 1L, 50_000.0},
                new Object[]{RepartoPago.POR_LIQUIDAR, 3L, 450_000.0},
                new Object[]{RepartoPago.FALLIDO, 1L, 20_000.0},
                new Object[]{RepartoPago.LIQUIDADO, 9L, 2_000_000.0},
                new Object[]{RepartoPago.ANULADO, 1L, 70_000.0}));

        var l = servicio.informes("A", 6, OCT_2026, AHORA).liquidacion();

        assertEquals(350_000.0, l.enEspera());
        assertEquals(3, l.lineasEnEspera());
        assertEquals(470_000.0, l.porLiquidar());
        assertEquals(4, l.lineasPorLiquidar());
        assertEquals(2_000_000.0, l.liquidado());
        assertEquals(70_000.0, l.anulado());
    }

    @Test
    void losMesesTienenTope() {
        assertEquals(6, InformesTiendaService.meses(0));
        assertEquals(24, InformesTiendaService.meses(500));
        assertEquals(12, InformesTiendaService.meses(12));
        assertNotNull(Collections.emptyList());
    }
}
