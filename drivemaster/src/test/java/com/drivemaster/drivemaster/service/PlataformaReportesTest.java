package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ClientesConMasCompras;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ComisionNeta;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ProductosMasVendidos;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.ServiciosPorTaller;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.UsuarioFila;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.UsuariosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ReportesPlataformaDTO.VentasPorTienda;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Reportes de la plataforma: calculan sobre pedidos, pagos y órdenes (nunca Venta) y no llevan datos personales. */
class PlataformaReportesTest {

    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);

    private MongoTemplate mongo;
    private RepartoPagoRepository repartos;
    private PlataformaReportesService reportes;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        repartos = mock(RepartoPagoRepository.class);
        reportes = new PlataformaReportesService(mongo, repartos);
    }

    @SuppressWarnings("unchecked")
    private static AggregationResults<Document> resultado(Document... filas) {
        AggregationResults<Document> r = mock(AggregationResults.class);
        when(r.getMappedResults()).thenReturn(List.of(filas));
        return r;
    }

    private static String pipeline(Aggregation a) {
        return a.toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
    }

    // ─── Ventas por tienda y mes ─────────────────────────────────────────────

    @Test
    void ventasPorTiendaYMesAlineaLosMesesSumaYOrdenaPorVentas() {
        AggregationResults<Document> filas = resultado(
                new Document("_id", new Document("tiendaId", "t1").append("mes", "2026-09")).append("tienda", "Norte").append("pedidos", 2).append("ventas", 100_000.0),
                new Document("_id", new Document("tiendaId", "t1").append("mes", "2026-10")).append("tienda", "Norte").append("pedidos", 1).append("ventas", 50_000.0),
                new Document("_id", new Document("tiendaId", "t2").append("mes", "2026-10")).append("tienda", "Sur").append("pedidos", 4).append("ventas", 400_000.0));
        when(mongo.aggregate(any(Aggregation.class), eq("pedidos_tienda"), eq(Document.class))).thenReturn(filas);

        VentasPorTienda r = reportes.ventasPorTienda(3, OCT_2026);

        assertEquals(List.of("2026-08", "2026-09", "2026-10"), r.meses());
        assertEquals("Sur", r.tiendas().get(0).tienda(), "la que más vendió va primero");
        var norte = r.tiendas().get(1);
        assertEquals(150_000.0, norte.ventas());
        assertEquals(3, norte.pedidos());
        assertEquals(List.of(0.0, 100_000.0, 50_000.0), norte.porMes().stream().map(m -> m.ventas()).toList());
        assertEquals(List.of(0L, 2L, 5L), r.totalesPorMes().stream().map(m -> m.pedidos()).toList());
        assertEquals(450_000.0, r.totalesPorMes().get(2).ventas());

        ArgumentCaptor<Aggregation> agg = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(agg.capture(), eq("pedidos_tienda"), eq(Document.class));
        String p = pipeline(agg.getValue());
        assertTrue(p.contains("$dateToString") && p.contains("America/Bogota"), "el mes se calcula en hora de Colombia: " + p);
        assertTrue(p.contains("NO_DESPACHADO") && p.contains("NO_ENTREGADO"), "no cuenta lo que no se despachó ni se entregó: " + p);
        assertFalse(p.toLowerCase().contains("venta\""), "no se calcula sobre Venta");
    }

    // ─── Zona horaria de Colombia (UTC-5) ────────────────────────────────────

    private Document etapa(Aggregation a, String operador) {
        return a.toPipeline(Aggregation.DEFAULT_CONTEXT).stream().filter(d -> d.containsKey(operador)).findFirst().orElseThrow();
    }

    @Test
    void elAgrupadoPorMesDeLasVentasUsaLaHoraDeColombiaYElInicioDelPeriodoTambien() {
        AggregationResults<Document> vacio = resultado();
        when(mongo.aggregate(any(Aggregation.class), eq("pedidos_tienda"), eq(Document.class))).thenReturn(vacio);

        reportes.ventasPorTienda(1, OCT_2026);

        ArgumentCaptor<Aggregation> agg = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(agg.capture(), eq("pedidos_tienda"), eq(Document.class));

        // 1. El mes se calcula con $dateToString en America/Bogota
        Document mes = (Document) ((Document) ((Document) etapa(agg.getValue(), "$project").get("$project")).get("mes")).get("$dateToString");
        assertEquals("America/Bogota", mes.getString("timezone"));
        assertEquals("%Y-%m", mes.getString("format"));
        java.time.ZoneId zona = java.time.ZoneId.of(mes.getString("timezone"));
        java.time.format.DateTimeFormatter formato = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");

        // Compra a las 11 p. m. del 31 de octubre en Colombia = 1 de noviembre 04:00 UTC: sigue siendo octubre
        Instant onceDeLaNoche = Instant.parse("2026-11-01T04:00:00Z");
        assertEquals("2026-10", formato.format(onceDeLaNoche.atZone(zona)), "cae en el mes en que se hizo");
        assertEquals("2026-11", formato.format(onceDeLaNoche.atZone(java.time.ZoneOffset.UTC)), "en UTC habría caído en el siguiente");
        // Un minuto después de la medianoche de Colombia ya es noviembre
        assertEquals("2026-11", formato.format(Instant.parse("2026-11-01T05:00:00Z").atZone(zona)));

        // 2. El período arranca a las 00:00 de Colombia del primer mes (05:00 UTC), no a las 00:00 UTC
        Document filtro = (Document) etapa(agg.getValue(), "$match").get("$match");
        java.util.Date gte = (java.util.Date) ((Document) filtro.get("fechaCreacion")).get("$gte");
        assertEquals(Instant.parse("2026-10-01T05:00:00Z"), gte.toInstant());
    }

    private ComisionNeta comisionConServidorEn(String zonaDelServidor, List<RepartoPago> lineas, YearMonth hasta, int meses) {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zonaDelServidor));
            when(repartos.comisionDesde(any())).thenReturn(lineas);
            return reportes.comisionNeta(meses, hasta, LocalDateTime.of(2026, 11, 5, 9, 0));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @Test
    void laComisionDeUnaCompraDeLas11PMDelUltimoDiaCaeEnSuMesAunqueElServidorEsteEnUTC() {
        // Servidor en UTC (como Render): una compra a las 11 p. m. del 31 de octubre en Colombia se guarda como 01-nov 04:00
        ComisionNeta utc = comisionConServidorEn("UTC", List.of(
                linea(LocalDateTime.of(2026, 11, 1, 4, 0), 7_000, "CATALOGO"),   // 23:00 del 31-oct en Colombia
                linea(LocalDateTime.of(2026, 11, 1, 5, 0), 3_000, "CATALOGO")),  // 00:00 del 1-nov en Colombia
                OCT_2026.plusMonths(1), 2);

        assertEquals(List.of("2026-10", "2026-11"), utc.meses().stream().map(m -> m.mes()).toList());
        assertEquals(7_000.0, utc.meses().get(0).neta(), "las 11 p. m. del 31 de octubre son de octubre");
        assertEquals(3_000.0, utc.meses().get(1).neta(), "la medianoche de Colombia ya es noviembre");

        // Servidor en hora de Colombia: la misma compra se guarda como 31-oct 23:00 y da el mismo resultado
        ComisionNeta bogota = comisionConServidorEn("America/Bogota", List.of(
                linea(LocalDateTime.of(2026, 10, 31, 23, 0), 7_000, "CATALOGO"),
                linea(LocalDateTime.of(2026, 11, 1, 0, 0), 3_000, "CATALOGO")),
                OCT_2026.plusMonths(1), 2);
        assertEquals(7_000.0, bogota.meses().get(0).neta());
        assertEquals(3_000.0, bogota.meses().get(1).neta());
    }

    @Test
    void laComisionPideDesdeElInicioDelPrimerMesEnColombiaYNoDesdeLasCeroDelServidor() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            when(repartos.comisionDesde(any())).thenReturn(List.of());

            reportes.comisionNeta(1, OCT_2026, LocalDateTime.of(2026, 10, 10, 9, 0));

            ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repartos).comisionDesde(desde.capture());
            // 1-oct 00:00 en Colombia = 05:00 en un servidor UTC
            assertEquals(LocalDateTime.of(2026, 10, 1, 5, 0), desde.getValue());
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    // ─── Productos más vendidos ──────────────────────────────────────────────

    @Test
    void productosMasVendidosDesdeLosPedidosConTopAcotado() {
        AggregationResults<Document> filas = resultado(
                new Document("_id", "p1").append("nombre", "Filtro").append("tienda", "Norte").append("unidades", 9).append("ventas", 90_000.0));
        when(mongo.aggregate(any(Aggregation.class), eq("pedidos_tienda"), eq(Document.class))).thenReturn(filas);

        ProductosMasVendidos r = reportes.productosMasVendidos(6, 5000, OCT_2026);

        assertEquals(6, r.meses());
        assertEquals("Filtro", r.productos().get(0).nombre());
        assertEquals(9, r.productos().get(0).unidades());
        ArgumentCaptor<Aggregation> agg = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(agg.capture(), eq("pedidos_tienda"), eq(Document.class));
        String p = pipeline(agg.getValue());
        assertTrue(p.contains("$unwind") && p.contains("items"), p);
        assertTrue(p.contains("$limit") && p.contains(String.valueOf(PlataformaReportesService.LIMITE_MAXIMO)), "tope de filas: " + p);
    }

    // ─── Clientes con más compras ────────────────────────────────────────────

    @Test
    void clientesConMasComprasSoloLlevaNombreAbreviadoYCiudad() {
        AggregationResults<Document> filas = resultado(new Document("_id", "u1").append("compras", 7).append("total", 700_000.0));
        when(mongo.aggregate(any(Aggregation.class), eq(CompraCatalogo.class), eq(Document.class))).thenReturn(filas);
        when(mongo.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of(Usuario.builder().id("u1")
                .nombre("Ana Pérez Gómez").correo("ana@gmail.com").telefono("3001234567").direccion("Cra 1 # 2-3")
                .identificacion("1234567890").ciudad("Medellín").build()));

        ClientesConMasCompras r = reportes.clientesConMasCompras(12, 10, OCT_2026);

        var c = r.clientes().get(0);
        assertEquals("Ana P.", c.cliente());
        assertEquals("Medellín", c.ciudad());
        assertEquals(7, c.compras());
        assertEquals(700_000.0, c.total());
        String datos = c.toString();
        for (String prohibido : new String[]{"ana@gmail.com", "3001234567", "Cra 1", "1234567890", "Pérez", "Gómez"}) {
            assertFalse(datos.contains(prohibido), prohibido);
        }
        ArgumentCaptor<Aggregation> agg = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(agg.capture(), eq(CompraCatalogo.class), eq(Document.class));
        assertTrue(pipeline(agg.getValue()).contains(CompraCatalogo.PAGADA), "solo compras pagadas");
    }

    // ─── Servicios por taller ────────────────────────────────────────────────

    @Test
    void serviciosPorTallerClasificaPorEstadoYSumaLoRepartido() {
        AggregationResults<Document> filas = resultado(
                new Document("_id", new Document("tallerId", "t1").append("estado", "ENTREGADA")).append("n", 4),
                new Document("_id", new Document("tallerId", "t1").append("estado", "EN_INSTALACION")).append("n", 2),
                new Document("_id", new Document("tallerId", "t1").append("estado", "CANCELADA")).append("n", 1),
                new Document("_id", new Document("tallerId", "t2").append("estado", "LISTA")).append("n", 1));
        when(mongo.aggregate(any(Aggregation.class), eq(OrdenServicio.class), eq(Document.class))).thenReturn(filas);
        when(mongo.find(any(Query.class), eq(Taller.class))).thenReturn(List.of(Taller.builder().id("t1").nombre("AutoMax").build()));
        when(repartos.sumaRepartidaPorTaller()).thenReturn(java.util.Collections.singletonList(new Object[]{"t1", 800_000.0}));

        ServiciosPorTaller r = reportes.serviciosPorTaller();

        var t1 = r.talleres().get(0);
        assertEquals("AutoMax", t1.taller());
        assertEquals(7, t1.total());
        assertEquals(4, t1.entregadas());
        assertEquals(2, t1.enCurso());
        assertEquals(1, t1.canceladas());
        assertEquals(800_000.0, t1.movidoAlTaller());
        assertEquals(0.0, r.talleres().get(1).movidoAlTaller());
    }

    // ─── Comisión registrada neta ────────────────────────────────────────────

    private static RepartoPago linea(LocalDateTime fecha, double monto, String concepto) {
        return RepartoPago.builder().beneficiarioTipo(RepartoPago.PLATAFORMA).estado(RepartoPago.INFORMATIVA)
                .monto(monto).concepto(concepto).fechaCreacion(fecha).build();
    }

    @Test
    void laComisionNetaDescuentaLoAnuladoPorReembolsosMesPorMes() {
        when(repartos.comisionDesde(any())).thenReturn(List.of(
                linea(LocalDateTime.of(2026, 9, 5, 10, 0), 10_000, "CATALOGO"),
                linea(LocalDateTime.of(2026, 10, 2, 10, 0), 20_000, "CATALOGO"),
                linea(LocalDateTime.of(2026, 10, 3, 10, 0), 5_000, "DIAGNOSTICO"),
                linea(LocalDateTime.of(2026, 10, 9, 10, 0), -8_000, "CATALOGO")));

        ComisionNeta r = reportes.comisionNeta(2, OCT_2026, LocalDateTime.of(2026, 10, 10, 9, 0));

        assertEquals(List.of("2026-09", "2026-10"), r.meses().stream().map(m -> m.mes()).toList());
        assertEquals(10_000.0, r.meses().get(0).neta());
        assertEquals(25_000.0, r.meses().get(1).registrada());
        assertEquals(8_000.0, r.meses().get(1).anulada());
        assertEquals(17_000.0, r.meses().get(1).neta());
        assertEquals(35_000.0, r.registrada());
        assertEquals(8_000.0, r.anulada());
        assertEquals(27_000.0, r.neta());
        assertEquals(22_000.0, r.netaPorConcepto().get("CATALOGO"));
        assertEquals(5_000.0, r.netaPorConcepto().get("DIAGNOSTICO"));
    }

    @Test
    void mesesYLimiteTienenTope() {
        assertEquals(12, PlataformaReportesService.meses(0));
        assertEquals(24, PlataformaReportesService.meses(999));
        assertEquals(10, PlataformaReportesService.limite(-1));
        assertEquals(50, PlataformaReportesService.limite(999));
        assertEquals("Ana P.", PlataformaReportesService.abreviar("Ana pérez gómez"));
        assertEquals("Ana", PlataformaReportesService.abreviar("Ana"));
        assertNull(PlataformaReportesService.abreviar(" "));
    }

    // ─── Usuarios ────────────────────────────────────────────────────────────

    private UsuariosRespuesta listarUsuarios(String q, String rol, String estado) {
        Usuario admin = Usuario.builder().id("u1").nombre("Luis").correo("luis.mora@drivemaster.co").rol("ADMIN")
                .activo(true).bloqueado(false).fechaCreacion(Instant.parse("2026-05-01T10:00:00Z")).password("hash").build();
        when(mongo.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of(admin));
        when(mongo.count(any(Query.class), eq(Usuario.class))).thenReturn(1L);
        AggregationResults<Document> roles = resultado(new Document("_id", "ADMIN").append("n", 2), new Document("_id", "TIENDA").append("n", 5));
        when(mongo.aggregate(any(Aggregation.class), eq(Usuario.class), eq(Document.class))).thenReturn(roles);
        return new PlataformaConsultasService(mongo).usuarios(q, rol, estado, 0, 20);
    }

    @Test
    void laListaDeUsuariosEnmascaraElCorreoYFiltraPorRolEstadoYBusqueda() {
        UsuariosRespuesta r = listarUsuarios("lu", "admin", "activos");

        UsuarioFila fila = r.usuarios().items().get(0);
        assertEquals("lu***@drivemaster.co", fila.correo());
        assertEquals("ADMIN", fila.rol());
        assertTrue(fila.activo());
        assertEquals(5L, r.porRol().get("TIENDA"));
        String datos = fila.toString();
        assertFalse(datos.contains("luis.mora") || datos.contains("hash"));

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(Usuario.class));
        String filtro = q.getValue().getQueryObject().toJson();
        assertTrue(filtro.contains("ADMIN") && filtro.contains("activo") && filtro.contains("\\\\Qlu\\\\E") || filtro.contains("\\Qlu\\E"), filtro);
    }

    @Test
    void sinRolSoloSeListanCuentasDelSistemaYUnRolInventadoNoDevuelveNada() {
        listarUsuarios(null, null, null);
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(Usuario.class));
        String filtro = q.getValue().getQueryObject().toJson();
        assertFalse(filtro.contains("CLIENTE"), "los clientes tienen su propia pantalla: " + filtro);
        assertTrue(filtro.contains("SUPERADMIN") && filtro.contains("TIENDA"), filtro);

        reset(mongo);
        listarUsuarios(null, "CLIENTE", null);
        ArgumentCaptor<Query> q2 = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q2.capture(), eq(Usuario.class));
        assertTrue(q2.getValue().getQueryObject().toJson().contains("__ninguno__"));
    }
}
