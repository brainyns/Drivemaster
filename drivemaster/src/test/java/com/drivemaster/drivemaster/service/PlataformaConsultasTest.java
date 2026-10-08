package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClienteFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ClientesRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.Operacion;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidoFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidosRespuesta;
import com.drivemaster.drivemaster.dto.plataforma.ProblemaEnvioDTO;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductoFila;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.ProductosRespuesta;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Productos y clientes del panel (solo lectura, filtros en el servidor) y panel operativo del ADMIN. */
class PlataformaConsultasTest {

    private MongoTemplate mongo;
    private PlataformaConsultasService servicio;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        servicio = new PlataformaConsultasService(mongo);
    }

    private static Producto producto(String id, String tienda) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo("C-" + id);
        p.setNombre("Filtro " + id);
        p.setCategoria("Motor");
        p.setMarca("Bosch");
        p.setTipo("STOCK");
        p.setTiendaId(tienda);
        p.setStockActual(4);
        p.setPrecioVenta(35_000.0);
        p.setActivo(true);
        return p;
    }

    private Query consultaDe(Class<?> tipo) {
        ArgumentCaptor<Query> c = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(c.capture(), eq(tipo));
        return c.getValue();
    }

    // ─── Productos ───────────────────────────────────────────────────────────

    @Test
    void losProductosSeFiltranPorTiendaYPorNuevosEnElServidor() {
        when(mongo.count(any(Query.class), eq(Producto.class))).thenReturn(1L);
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(producto("507f1f77bcf86cd799439011", "t1")));
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(Tienda.builder().id("t1").nombre("Repuestos Sur").build()));
        when(mongo.findAll(Tienda.class)).thenReturn(List.of(Tienda.builder().id("t1").nombre("Repuestos Sur").build()));

        ProductosRespuesta r = servicio.productos(null, "t1", 7, "activos", 0, 20);

        String filtro = consultaDe(Producto.class).getQueryObject().toJson();
        assertTrue(filtro.contains("\"tiendaId\": \"t1\""), filtro);
        assertTrue(filtro.contains("_id"), "los nuevos se cuentan por el _id de Mongo: " + filtro);
        assertEquals(1, r.productos().total());
        ProductoFila fila = r.productos().items().get(0);
        assertEquals("Repuestos Sur", fila.tienda());
        assertEquals(4, fila.stock());
        assertNotNull(fila.fechaIngreso(), "los anteriores al campo fechaCreacion toman la fecha de su _id");
        assertEquals("t1", r.tiendas().get(0).id());
    }

    @Test
    void laBusquedaEsLiteralYNoUnaExpresionRegular() {
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of());
        when(mongo.findAll(Tienda.class)).thenReturn(List.of());

        servicio.productos("a.b(", null, null, null, 0, 20);

        String filtro = consultaDe(Producto.class).getQueryObject().toJson();
        assertTrue(filtro.contains("\\\\Qa.b(\\\\E") || filtro.contains("\\Qa.b(\\E"), "texto escapado: " + filtro);
    }

    @Test
    void elTamanoDePaginaTieneTope() {
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of());
        when(mongo.findAll(Tienda.class)).thenReturn(List.of());

        assertEquals(50, servicio.productos(null, null, null, null, 0, 5000).productos().tamano());
        assertEquals(20, servicio.productos(null, null, null, null, 0, 0).productos().tamano());
        assertEquals(0, servicio.productos(null, null, null, null, -3, 10).productos().pagina());
    }

    @Test
    void laFechaDeIngresoPrefiereElCampoYSinEllaUsaElIdDeMongo() {
        LocalDateTime propia = LocalDateTime.of(2026, 3, 1, 10, 0);
        Producto conCampo = producto("507f1f77bcf86cd799439011", "t1");
        conCampo.setFechaCreacion(propia);
        assertEquals(propia, PlataformaConsultasService.fechaIngreso(conCampo));

        String id = new ObjectId(Date.from(Instant.parse("2026-01-15T12:00:00Z"))).toHexString();
        assertEquals(2026, PlataformaConsultasService.fechaIngreso(producto(id, "t1")).getYear());
        assertNull(PlataformaConsultasService.fechaIngreso(producto("sin-formato", "t1")));
    }

    // ─── Clientes ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private AggregationResults<Document> resultado(Document... filas) {
        AggregationResults<Document> r = mock(AggregationResults.class);
        when(r.getMappedResults()).thenReturn(List.of(filas));
        return r;
    }

    @Test
    void losClientesMuestranSoloLoNecesarioConElCorreoEnmascaradoYSusConteos() {
        Usuario ana = Usuario.builder().id("u1").nombre("Ana Pérez").correo("ana.perez@gmail.com").ciudad("Medellín")
                .telefono("3001234567").direccion("Cra 1 # 2-3").identificacion("1234567890").rol("CLIENTE").activo(true)
                .password("hash").build();
        when(mongo.count(any(Query.class), eq(Usuario.class))).thenReturn(1L);
        when(mongo.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of(ana));
        when(mongo.findDistinct(any(Query.class), eq("ciudad"), eq(Usuario.class), eq(String.class))).thenReturn(List.of("Medellín", "Bogotá"));
        AggregationResults<Document> deCompras = resultado(new Document("_id", "u1").append("n", 3));
        AggregationResults<Document> deServicios = resultado(new Document("_id", "u1").append("n", 2));
        when(mongo.aggregate(any(Aggregation.class), eq(CompraCatalogo.class), eq(Document.class))).thenReturn(deCompras);
        when(mongo.aggregate(any(Aggregation.class), eq(OrdenServicio.class), eq(Document.class))).thenReturn(deServicios);

        ClientesRespuesta r = servicio.clientes("ana", "Medellín", "activos", 0, 20);

        ClienteFila fila = r.clientes().items().get(0);
        assertEquals("an***@gmail.com", fila.correo());
        assertEquals(3, fila.compras());
        assertEquals(2, fila.servicios());
        assertEquals("Medellín", fila.ciudad());
        assertEquals(List.of("Bogotá", "Medellín"), r.ciudades());
        // El DTO no tiene dónde llevar teléfono, dirección, documento ni contraseña
        String datos = fila.toString();
        assertFalse(datos.contains("3001234567") || datos.contains("Cra 1") || datos.contains("1234567890") || datos.contains("hash"));

        String filtro = consultaDe(Usuario.class).getQueryObject().toJson();
        assertTrue(filtro.contains("CLIENTE") && filtro.contains("Medell"), filtro);
    }

    @Test
    void soloSeConsultanLosConteosDeLosClientesDeLaPagina() {
        when(mongo.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of());
        when(mongo.findDistinct(any(Query.class), eq("ciudad"), eq(Usuario.class), eq(String.class))).thenReturn(List.of());

        servicio.clientes(null, null, null, 0, 20);

        verify(mongo, never()).aggregate(any(Aggregation.class), eq(CompraCatalogo.class), eq(Document.class));
    }

    @Test
    void elCorreoSeEnmascaraSinExponerloCompleto() {
        assertEquals("an***@gmail.com", PlataformaConsultasService.enmascarar("ana.perez@gmail.com"));
        assertEquals("x***@d.co", PlataformaConsultasService.enmascarar("x@d.co"));
        assertNull(PlataformaConsultasService.enmascarar(null));
        assertNull(PlataformaConsultasService.enmascarar("sin-arroba"));
    }

    // ─── Pedidos y envíos ────────────────────────────────────────────────────

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 7, 12, 0);

    private static PedidoTienda pedidoEnvio() {
        return PedidoTienda.builder().id("p1").origen(PedidoTienda.ORIGEN_CATALOGO).entrega(PedidoTienda.ENVIO)
                .estado(PedidoTienda.PENDIENTE).tiendaId("t1").tiendaNombre("Repuestos Sur")
                .clienteId("u1").clienteNombre("Ana Pérez").envioReceptor("Ana Pérez").envioTelefono("3001234567")
                .envioDireccion("Cra 10 # 20-30").envioCiudad("Medellín").codigoRetiroHash("hash-secreto")
                .fechaCreacion(AHORA.minusDays(4)).plazoDespachoHasta(AHORA.minusDays(1)).total(150_000.0)
                .items(List.of(com.drivemaster.drivemaster.model.CotizacionItem.builder().cantidad(2).build(),
                        com.drivemaster.drivemaster.model.CotizacionItem.builder().cantidad(1).build())).build();
    }

    @Test
    void laFilaDelMonitorCalculaDiasYAtrasoYNoLlevaDatosDelCliente() {
        PedidoFila f = PlataformaConsultasService.fila(pedidoEnvio(), AHORA);

        assertEquals(4, f.diasDesdePago());
        assertTrue(f.atrasado());
        assertEquals("Medellín", f.destino());
        assertEquals(3, f.articulos());
        assertFalse(f.problemaAbierto());
        // Sin nombre, teléfono, dirección, montos ni el hash del código de retiro
        String datos = f.toString();
        for (String prohibido : new String[]{"Ana", "3001234567", "Cra 10", "150000", "hash-secreto", "u1"}) {
            assertFalse(datos.contains(prohibido), prohibido);
        }
    }

    @Test
    void unPedidoYaDespachadoONoVencidoNoEstaAtrasado() {
        PedidoTienda despachado = pedidoEnvio().toBuilder().estado(PedidoTienda.DESPACHADO).build();
        PedidoTienda aTiempo = pedidoEnvio().toBuilder().plazoDespachoHasta(AHORA.plusDays(1)).build();

        assertFalse(PlataformaConsultasService.fila(despachado, AHORA).atrasado());
        assertFalse(PlataformaConsultasService.fila(aTiempo, AHORA).atrasado());
    }

    @Test
    void elDestinoDeUnRetiroEsLaTiendaYElDeUnPedidoDeTallerEsElTaller() {
        PedidoTienda retiro = pedidoEnvio().toBuilder().entrega(PedidoTienda.RETIRO).tiendaCiudad("Bogotá").build();
        PedidoTienda deTaller = PedidoTienda.builder().id("p2").origen(PedidoTienda.ORIGEN_TALLER).estado(PedidoTienda.PENDIENTE)
                .tallerNombre("AutoMax").tallerCiudad("Cali").fechaCreacion(AHORA).build();

        assertEquals("Retiro en tienda · Bogotá", PlataformaConsultasService.fila(retiro, AHORA).destino());
        assertEquals("Taller AutoMax · Cali", PlataformaConsultasService.fila(deTaller, AHORA).destino());
    }

    @SuppressWarnings("unchecked")
    private PedidosRespuesta consultarPedidos(String estado, String tienda, String entrega, boolean atrasados) {
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of(pedidoEnvio()));
        when(mongo.findAll(Tienda.class)).thenReturn(List.of());
        AggregationResults<Document> grupos = mock(AggregationResults.class);
        when(grupos.getMappedResults()).thenReturn(List.of(new Document("_id", "PENDIENTE").append("n", 5)));
        when(mongo.aggregate(any(Aggregation.class), eq("pedidos_tienda"), eq(Document.class))).thenReturn(grupos);
        return servicio.pedidos(estado, tienda, entrega, atrasados, 0, 20, AHORA);
    }

    @Test
    void elMonitorFiltraPorEstadoTiendaEntregaYAtrasoEnElServidor() {
        PedidosRespuesta r = consultarPedidos("pendiente", "t1", "ENVIO", true);

        String filtro = consultaDe(PedidoTienda.class).toString();
        assertTrue(filtro.contains("PENDIENTE") && filtro.contains("t1") && filtro.contains("ENVIO"), filtro);
        assertTrue(filtro.contains("plazoDespachoHasta"), "atrasados: " + filtro);
        assertEquals(1, r.pedidos().items().size());
        assertEquals(5L, r.porEstado().get("PENDIENTE"));
    }

    @Test
    void unEstadoInventadoNoDevuelveNadaEnVezDeIgnorarseElFiltro() {
        consultarPedidos("DROP", null, null, false);

        assertTrue(consultaDe(PedidoTienda.class).toString().contains("__ninguno__"));
    }

    @Test
    void elFiltroProblemaMuestraSoloLosProblemasAbiertos() {
        consultarPedidos("PROBLEMA", null, null, false);

        String filtro = consultaDe(PedidoTienda.class).toString();
        assertTrue(filtro.contains("problemaReportado") && filtro.contains("resolucionProblema"), filtro);
    }

    // ─── Problemas de envío: datos mínimos ───────────────────────────────────

    @Test
    void losProblemasDeEnvioNoExponenElHashDelCodigoNiLosDatosPersonales() {
        PedidoTienda p = pedidoEnvio().toBuilder().problemaReportado(true).problemaDetalle("No llegó")
                .estado(PedidoTienda.DESPACHADO).build();

        ProblemaEnvioDTO dto = ProblemaEnvioDTO.de(p);

        assertEquals("p1", dto.id());
        assertEquals("Medellín", dto.envioCiudad());
        assertEquals("No llegó", dto.problemaDetalle());
        String datos = dto.toString();
        for (String prohibido : new String[]{"hash-secreto", "3001234567", "Cra 10", "Ana Pérez", "u1"}) {
            assertFalse(datos.contains(prohibido), prohibido);
        }
        for (var campo : ProblemaEnvioDTO.class.getRecordComponents()) {
            String n = campo.getName().toLowerCase();
            assertFalse(n.contains("hash") || n.contains("telefono") || n.contains("receptor") || n.contains("direccion")
                    || n.contains("cliente"), "campo con datos personales: " + n);
        }
        assertNull(ProblemaEnvioDTO.de(null));
    }

    // ─── Panel operativo del ADMIN ───────────────────────────────────────────

    @Test
    void elPanelDelAdminSoloTieneSusAlertasOperativas() {
        when(mongo.count(any(Query.class), eq(Tienda.class))).thenReturn(2L);
        when(mongo.count(any(Query.class), eq(Taller.class))).thenReturn(1L);
        when(mongo.count(any(Query.class), eq(PedidoTienda.class))).thenAnswer(i -> {
            Object consulta = i.getArgument(0);
            String q = consulta.toString();
            return q.contains("problemaReportado") ? 4L : 7L;
        });

        Operacion o = new PlataformaDashboardService(mongo, mock(PagoRepository.class), mock(RepartoPagoRepository.class),
                mock(ReembolsoRepository.class), mock(SuscripcionRepository.class), mock(ParametrosMarketplace.class))
                .operacion(LocalDateTime.of(2026, 10, 7, 9, 0));

        assertEquals(2, o.tiendasPorAprobar());
        assertEquals(1, o.talleresPorAprobar());
        assertEquals(4, o.problemasEnvioAbiertos());
        assertEquals(7, o.despachosAtrasados());
        // Sin dinero ni datos de la plataforma: el registro solo lleva estos campos
        assertEquals(5, Operacion.class.getRecordComponents().length);
    }
}
