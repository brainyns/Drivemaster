package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegocioFila;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegocioPendiente;
import com.drivemaster.drivemaster.dto.plataforma.NegociosPlataformaDTO.NegociosRespuesta;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.PlanSuscripcion;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.repository.mysql.PlanSuscripcionRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tiendas y talleres unidos con las aprobaciones: lista completa para el SUPERADMIN y vista mínima para el ADMIN. */
class PlataformaNegociosTest {

    private MongoTemplate mongo;
    private PlataformaNegociosService servicio;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
        PlanSuscripcionRepository planes = mock(PlanSuscripcionRepository.class);
        PlanSuscripcion basico = new PlanSuscripcion();
        basico.setCodigo("BASICO");
        basico.setNombre("Plan básico");
        when(planes.findAll()).thenReturn(List.of(basico));
        Suscripcion s = new Suscripcion();
        s.setActorTipo("TIENDA");
        s.setActorId("t1");
        s.setPlanCodigo("BASICO");
        s.setEstado(Suscripcion.ACTIVA);
        s.setFechaFin(LocalDateTime.of(2026, 12, 1, 0, 0));
        when(suscripciones.findByEstado(Suscripcion.ACTIVA)).thenReturn(List.of(s));
        servicio = new PlataformaNegociosService(mongo, suscripciones, planes);
    }

    @SuppressWarnings("unchecked")
    private static AggregationResults<Document> resultado(Document... filas) {
        AggregationResults<Document> r = mock(AggregationResults.class);
        when(r.getMappedResults()).thenReturn(List.of(filas));
        return r;
    }

    private void datos() {
        Tienda completa = Tienda.builder().id("t1").nombre("Repuestos Norte").ciudad("Medellín").direccion("Cra 1").horario("L-V 8-6")
                .estadoAprobacion("APROBADO").activo(true).fechaRegistro(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
        Tienda incompleta = Tienda.builder().id("t2").nombre("Frenos Sur").ciudad("Cali")
                .estadoAprobacion("PENDIENTE").fechaRegistro(LocalDateTime.of(2026, 10, 5, 10, 0)).build();
        Taller conNotas = Taller.builder().id("w1").nombre("AutoMax").ciudad("Bogotá").direccion("Cl 2").telefono("300").horario("L-S")
                .estadoAprobacion(null).activo(true).rating(4.9).fechaRegistro(LocalDateTime.of(2026, 5, 1, 10, 0)).build();
        Taller sinNotas = Taller.builder().id("w2").nombre("Taller Sin Notas").ciudad("Cali").rating(3.0)
                .estadoAprobacion("RECHAZADO").motivoRechazo("Documentos").fechaRegistro(LocalDateTime.of(2026, 8, 1, 10, 0)).build();
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(completa, incompleta));
        when(mongo.find(any(Query.class), eq(Taller.class))).thenReturn(List.of(conNotas, sinNotas));
        when(mongo.count(any(Query.class), eq(Tienda.class))).thenReturn(1L);
        when(mongo.count(any(Query.class), eq(Taller.class))).thenReturn(1L);

        AggregationResults<Document> total = resultado(new Document("_id", "t1").append("n", 12));
        AggregationResults<Document> activos = resultado(new Document("_id", "t1").append("n", 10));
        // Dos agregaciones de Producto: primero el total y después los activos
        when(mongo.aggregate(any(Aggregation.class), eq(Producto.class), eq(Document.class))).thenReturn(total, activos);
        AggregationResults<Document> ordenes = resultado(
                new Document("_id", new Document("tallerId", "w1").append("estado", "ENTREGADA")).append("n", 8),
                new Document("_id", new Document("tallerId", "w1").append("estado", "LISTA")).append("n", 2));
        AggregationResults<Document> notas = resultado(new Document("_id", "w1").append("promedio", 4.26).append("n", 5));
        when(mongo.aggregate(any(Aggregation.class), eq(OrdenServicio.class), eq(Document.class))).thenReturn(ordenes, notas);
    }

    @Test
    void unaSolaListaTrae_estado_plan_calificacion_actividad_y_datos_por_completar() {
        datos();

        NegociosRespuesta r = servicio.negocios(null, null, null, 0, 20);

        assertEquals(4, r.negocios().total());
        // Los pendientes van primero para que se vean
        assertEquals("t2", r.negocios().items().get(0).id());
        assertEquals("PENDIENTE", r.negocios().items().get(0).estado());

        NegocioFila tienda = r.negocios().items().stream().filter(f -> f.id().equals("t1")).findFirst().orElseThrow();
        assertEquals("TIENDA", tienda.tipo());
        assertEquals("Plan básico", tienda.plan().nombre());
        assertEquals(12L, tienda.productos());
        assertEquals(10L, tienda.productosActivos());
        assertNull(tienda.ordenes());
        assertNull(tienda.calificacion(), "las tiendas todavía no se califican");
        assertTrue(tienda.datosPorCompletar().isEmpty());

        NegocioFila taller = r.negocios().items().stream().filter(f -> f.id().equals("w1")).findFirst().orElseThrow();
        assertEquals("APROBADO", taller.estado(), "un taller anterior al marketplace cuenta como aprobado");
        assertEquals(10L, taller.ordenes());
        assertEquals(8L, taller.ordenesEntregadas());
        assertEquals(4.3, taller.calificacion());
        assertEquals(5L, taller.calificaciones());
        assertNull(taller.productos());
        assertNull(taller.plan());

        NegocioFila sinNotas = r.negocios().items().stream().filter(f -> f.id().equals("w2")).findFirst().orElseThrow();
        assertEquals(3.0, sinNotas.calificacion(), "sin calificaciones de clientes se usa la del taller");
        assertEquals(0L, sinNotas.calificaciones());
        assertEquals("Documentos", sinNotas.motivoRechazo());

        NegocioFila incompleta = r.negocios().items().get(0);
        assertEquals(List.of("dirección", "horario"), incompleta.datosPorCompletar());
        assertEquals(List.of("dirección", "teléfono", "horario"), sinNotas.datosPorCompletar());
    }

    @Test
    void filtraPorTipoEstadoYBusquedaYPagina() {
        datos();

        NegociosRespuesta soloTalleres = servicio.negocios("taller", null, null, 0, 20);
        assertTrue(soloTalleres.negocios().items().stream().allMatch(f -> f.tipo().equals("TALLER")));
        verify(mongo, never()).find(any(Query.class), eq(Tienda.class));

        datos();
        NegociosRespuesta pagina = servicio.negocios(null, null, null, 1, 3);
        assertEquals(1, pagina.negocios().items().size());
        assertEquals(4, pagina.negocios().total());
    }

    @Test
    void aprobadoIncluyeLasCuentasSinEstadoYUnEstadoInventadoNoDevuelveNada() {
        datos();
        servicio.negocios("TALLER", "aprobado", "auto", 0, 20);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(Taller.class));
        String filtro = q.getValue().getQueryObject().toJson();
        assertTrue(filtro.contains("APROBADO") && filtro.contains("null"), "incluye estado vacío: " + filtro);
        assertTrue(filtro.contains("\\Qauto\\E") || filtro.contains("\\\\Qauto\\\\E"), filtro);

        NegociosRespuesta nada = servicio.negocios(null, "INVENTADO", null, 0, 20);
        assertEquals(0, nada.negocios().total());
    }

    @Test
    void unTallerSinCalificacionesYNegociosSinLogoNoRompenLaLista() {
        // Desde el recálculo de calificaciones, un taller sin notas de clientes guarda rating null (y 0 calificaciones)
        Taller nuevo = Taller.builder().id("w4").nombre("Taller Nuevo").ciudad("Pasto").estadoAprobacion("PENDIENTE")
                .rating(null).calificaciones(0).logoUrl(null).fechaRegistro(LocalDateTime.of(2026, 10, 10, 10, 0)).build();
        Tienda sinLogo = Tienda.builder().id("t3").nombre("Sin Logo").ciudad("Pasto").logoUrl(null)
                .estadoAprobacion("APROBADO").fechaRegistro(LocalDateTime.of(2026, 10, 1, 10, 0)).build();
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(sinLogo));
        when(mongo.find(any(Query.class), eq(Taller.class))).thenReturn(List.of(nuevo));
        AggregationResults<Document> vacio = resultado();
        when(mongo.aggregate(any(Aggregation.class), eq(Producto.class), eq(Document.class))).thenReturn(vacio);
        when(mongo.aggregate(any(Aggregation.class), eq(OrdenServicio.class), eq(Document.class))).thenReturn(vacio);

        for (String tipo : new String[]{null, "TIENDA", "TALLER"}) {
            NegociosRespuesta r = assertDoesNotThrow(() -> servicio.negocios(tipo, null, null, 0, 20), "tipo " + tipo);
            assertFalse(r.negocios().items().isEmpty(), "tipo " + tipo);
        }

        NegocioFila taller = servicio.negocios("TALLER", null, null, 0, 20).negocios().items().get(0);
        assertNull(taller.calificacion(), "sin calificaciones es un taller nuevo, no un 0");
        assertEquals(0L, taller.calificaciones());
        assertEquals(0L, taller.ordenes());
    }

    @Test
    void cadaFilaTraeElLogoDelNegocioYSeFiltraPorPlan() {
        datos();
        Tienda conLogo = Tienda.builder().id("t1").nombre("Repuestos Norte").logoUrl("https://res.cloudinary.com/x/logo.png")
                .estadoAprobacion("APROBADO").build();
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(conLogo));

        NegociosRespuesta todos = servicio.negocios(null, null, null, 0, 20);
        NegocioFila tienda = todos.negocios().items().stream().filter(f -> f.id().equals("t1")).findFirst().orElseThrow();
        assertEquals("https://res.cloudinary.com/x/logo.png", tienda.logoUrl());
        assertNull(todos.negocios().items().stream().filter(f -> f.id().equals("w1")).findFirst().orElseThrow().logoUrl());

        // Solo t1 tiene suscripción activa (BASICO)
        datos();
        NegociosRespuesta basico = servicio.negocios(null, null, null, "basico", 0, 20);
        assertEquals(List.of("t1"), basico.negocios().items().stream().map(NegocioFila::id).toList());
        assertEquals(1, basico.negocios().total(), "el total cuenta solo los del plan");

        datos();
        NegociosRespuesta sinPlan = servicio.negocios(null, null, null, "SIN_PLAN", 0, 20);
        assertEquals(3, sinPlan.negocios().total());
        assertTrue(sinPlan.negocios().items().stream().allMatch(f -> f.plan() == null));

        datos();
        assertEquals(0, servicio.negocios(null, null, null, "PREMIUM", 0, 20).negocios().total());
        datos();
        assertEquals(4, servicio.negocios(null, null, null, " ", 0, 20).negocios().total(), "vacío = todos");
    }

    @Test
    void elAdminSoloRecibeLosPendientesConLoMinimoParaDecidir() {
        Tienda pendiente = Tienda.builder().id("t2").nombre("Frenos Sur").ciudad("Cali").nit("900123").estadoAprobacion("PENDIENTE")
                .fechaRegistro(LocalDateTime.of(2026, 10, 5, 10, 0)).build();
        Taller pendienteTaller = Taller.builder().id("w3").nombre("Mecánica Rápida").ciudad("Cali").direccion("Cl 5").estadoAprobacion("PENDIENTE")
                .fechaRegistro(LocalDateTime.of(2026, 10, 6, 10, 0)).build();
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(pendiente));
        when(mongo.find(any(Query.class), eq(Taller.class))).thenReturn(List.of(pendienteTaller));

        List<NegocioPendiente> r = servicio.pendientes();

        assertEquals(List.of("w3", "t2"), r.stream().map(NegocioPendiente::id).toList(), "el más reciente primero");
        assertEquals("900123", r.get(1).nit());
        // Sin plan, calificación, actividad ni motivo: la vista del ADMIN no tiene dónde llevarlos
        for (var campo : NegocioPendiente.class.getRecordComponents()) {
            String n = campo.getName().toLowerCase();
            assertFalse(n.contains("plan") || n.contains("calific") || n.contains("producto") || n.contains("orden")
                    || n.contains("telefono") || n.contains("correo"), "campo de más para el ADMIN: " + n);
        }
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(Taller.class));
        assertTrue(q.getValue().getQueryObject().toJson().contains("PENDIENTE"));
    }
}
