package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.config.LimpiezaTiendaPropia.Informe;
import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.testutil.LogsCapturados;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * La limpieza de la antigua "Tienda DriveMaster": no corre sola, tiene simulación, es idempotente, borra solo
 * productos, movimientos y la tienda, conserva ventas y solicitudes, y aborta sin tocar nada si algo no cuadra.
 */
class LimpiezaTiendaPropiaTest {

    private MongoTemplate mongo;
    // Cuántos documentos devuelve el conteo de cada clase (los que la prueba quiera cambiar)
    private final Map<Class<?>, Long> conteos = new HashMap<>();
    private final Map<String, Long> conteosAbiertos = new HashMap<>();

    private static Document tiendaPropia() {
        return new Document("_id", "tP").append("nombre", "DriveMaster").append("plataforma", true);
    }

    private static Producto producto(String id) {
        Producto p = new Producto();
        p.setId(id);
        p.setTiendaId("tP");
        return p;
    }

    private static boolean pideAbiertos(Query q) {
        String json = q.getQueryObject().toJson();
        return json.contains("PENDIENTE") || json.contains("EMITIDA");
    }

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        when(mongo.find(any(Query.class), eq(Document.class), eq("tiendas"))).thenReturn(List.of(tiendaPropia()));
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(producto("p1"), producto("p2"), producto("p3")));
        conteos.put(MovimientoInventario.class, 5L);
        conteos.put(Venta.class, 12L);
        conteos.put(Solicitud.class, 4L);
        conteos.put(Cotizacion.class, 2L);
        when(mongo.count(any(Query.class), any(Class.class))).thenAnswer(i -> {
            Query q = i.getArgument(0);
            Class<?> clase = i.getArgument(1);
            if (clase == Producto.class) return 2L; // productos sin tienda
            if (clase == MovimientoInventario.class && q.getQueryObject().toJson().contains("$ne")) return 0L;
            if ((clase == Venta.class || clase == Solicitud.class || clase == Cotizacion.class) && pideAbiertos(q)) {
                return conteosAbiertos.getOrDefault(clase.getSimpleName(), 0L);
            }
            return conteos.getOrDefault(clase, 0L);
        });
        when(mongo.remove(any(Query.class), eq(MovimientoInventario.class))).thenReturn(DeleteResult.acknowledged(5));
        when(mongo.remove(any(Query.class), eq(Producto.class))).thenReturn(DeleteResult.acknowledged(3));
        when(mongo.remove(any(Query.class), eq("tiendas"))).thenReturn(DeleteResult.acknowledged(1));
    }

    private LimpiezaTiendaPropia limpieza(boolean simular) {
        return new LimpiezaTiendaPropia(mongo, simular);
    }

    private void verificarQueNoSeBorroNada() {
        verify(mongo, never()).remove(any(Query.class), any(Class.class));
        verify(mongo, never()).remove(any(Query.class), anyString());
        verify(mongo, never()).remove(any(Object.class));
        verify(mongo, never()).dropCollection(any(Class.class));
    }

    // ─── Informe previo (simulación) ─────────────────────────────────────────

    @Test
    void laSimulacionCuentaTodoYNoBorraNada() {
        Informe r = limpieza(true).ejecutar();

        assertTrue(r.simulado());
        assertTrue(r.tiendaEncontrada());
        assertEquals(3, r.productos());
        assertEquals(2, r.productosSinTienda());
        assertEquals(5, r.movimientos());
        assertEquals(12, r.ventas());
        assertEquals(4, r.solicitudes());
        assertEquals(2, r.cotizaciones());
        assertTrue(r.bloqueos().isEmpty());
        assertEquals(0, r.productosBorrados() + r.movimientosBorrados() + r.tiendasBorradas());
        verificarQueNoSeBorroNada();
    }

    @Test
    void laSimulacionReportaLosBloqueosPeroNoFallaParaPoderInformar() {
        conteosAbiertos.put("Venta", 2L);
        conteosAbiertos.put("Solicitud", 1L);

        Informe r = limpieza(true).ejecutar();

        assertEquals(2, r.ventasAbiertas());
        assertEquals(1, r.solicitudesAbiertas());
        assertEquals(2, r.bloqueos().size());
        verificarQueNoSeBorroNada();
        String log = String.join("\n", LogsCapturados.de(() -> limpieza(true).ejecutar()));
        assertTrue(log.contains("NO se podría ejecutar todavía"), log);
        assertTrue(log.contains("12") && log.contains("sin cerrar"), log);
    }

    // ─── Ejecución ───────────────────────────────────────────────────────────

    @Test
    void borraProductosMovimientosYLaTiendaEnEseOrdenYReportaCuantos() {
        Informe r = limpieza(false).ejecutar();

        assertEquals(3, r.productosBorrados());
        assertEquals(5, r.movimientosBorrados());
        assertEquals(1, r.tiendasBorradas());

        InOrder orden = inOrder(mongo);
        orden.verify(mongo).remove(any(Query.class), eq(MovimientoInventario.class));
        orden.verify(mongo).remove(any(Query.class), eq(Producto.class));
        orden.verify(mongo).remove(any(Query.class), eq("tiendas"));

        // La tienda se borra solo si sigue marcada como propia, por su id
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(q.capture(), eq("tiendas"));
        assertTrue(q.getValue().getQueryObject().toJson().contains("tP") && q.getValue().getQueryObject().toJson().contains("plataforma"));
    }

    @Test
    void nuncaTocaVentasSolicitudesCotizacionesNiCompras() {
        limpieza(false).ejecutar();

        for (Class<?> historial : new Class<?>[]{Venta.class, Solicitud.class, Cotizacion.class, Compra.class, Proveedor.class,
                PedidoTienda.class, CompraCatalogo.class}) {
            verify(mongo, never()).remove(any(Query.class), eq(historial));
            verify(mongo, never()).updateMulti(any(Query.class), any(), eq(historial));
        }
        verify(mongo, never()).updateMulti(any(Query.class), any(), eq(Producto.class));
    }

    @Test
    void esIdempotenteSiYaNoHayTiendaPropiaNoHaceNada() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("tiendas"))).thenReturn(List.of());

        Informe r = limpieza(false).ejecutar();

        assertFalse(r.tiendaEncontrada());
        assertEquals(0, r.productosBorrados() + r.movimientosBorrados() + r.tiendasBorradas());
        verificarQueNoSeBorroNada();
    }

    // ─── Si algo no cuadra: aborta sin tocar nada ────────────────────────────

    private void abortaSinTocarNada(String contiene) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> limpieza(false).ejecutar());
        assertTrue(e.getMessage().contains(contiene), e.getMessage());
        verificarQueNoSeBorroNada();
    }

    @Test
    void conVentasSinCerrarConProductosDeLaTiendaAborta() {
        conteosAbiertos.put("Venta", 1L);
        abortaSinTocarNada("venta(s) sin cerrar");
    }

    @Test
    void conSolicitudesSinCerrarAborta() {
        conteosAbiertos.put("Solicitud", 3L);
        abortaSinTocarNada("solicitud(es) sin cerrar");
    }

    @Test
    void conCotizacionesDeTallerSinResolverAborta() {
        conteosAbiertos.put("Cotizacion", 1L);
        abortaSinTocarNada("cotización(es) de taller sin resolver");
    }

    @Test
    void conPedidosComprasOProveedoresAtadosALaTiendaAborta() {
        conteos.put(PedidoTienda.class, 2L);
        abortaSinTocarNada("pedido(s) de tienda");

        conteos.remove(PedidoTienda.class);
        conteos.put(CompraCatalogo.class, 1L);
        abortaSinTocarNada("compra(s) del catálogo");

        conteos.remove(CompraCatalogo.class);
        conteos.put(Compra.class, 1L);
        conteos.put(Proveedor.class, 1L);
        abortaSinTocarNada("compra(s) a proveedores");
    }

    @Test
    void conMasDeUnaTiendaPropiaOUnaConCuentaDeUsuarioAborta() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("tiendas")))
                .thenReturn(List.of(tiendaPropia(), new Document("_id", "otra").append("plataforma", true)));
        abortaSinTocarNada("2 tiendas marcadas como propias");

        when(mongo.find(any(Query.class), eq(Document.class), eq("tiendas")))
                .thenReturn(List.of(tiendaPropia().append("usuarioId", "u-real")));
        abortaSinTocarNada("tiene una cuenta de usuario");
    }

    @Test
    void elAbortoQuedaEnElLogYReuneTodosLosMotivos() {
        conteosAbiertos.put("Venta", 1L);
        conteos.put(PedidoTienda.class, 2L);

        List<String> logs = LogsCapturados.de(() -> assertThrows(IllegalStateException.class, () -> limpieza(false).ejecutar()));

        String todo = String.join("\n", logs);
        assertTrue(todo.contains("ABORTADA") && todo.contains("venta(s) sin cerrar") && todo.contains("pedido(s) de tienda"), todo);
    }

    // ─── No corre sola ───────────────────────────────────────────────────────

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withBean(MongoTemplate.class, () -> mongo)
                .withUserConfiguration(LimpiezaTiendaPropia.class);
    }

    @Test
    void sinElIndicadorExplicitoLaLimpiezaNiSiquieraExisteYNoSeBorraNada() {
        contexto().run(ctx -> assertFalse(ctx.containsBean("limpiezaTiendaPropia")));
        contexto().withPropertyValues("migracion.limpieza-tienda-propia.ejecutar=false")
                .run(ctx -> assertFalse(ctx.containsBean("limpiezaTiendaPropia")));
        verificarQueNoSeBorroNada();
    }

    @Test
    void conElIndicadorElRunnerExisteYHaceLaLimpieza() {
        contexto().withPropertyValues("migracion.limpieza-tienda-propia.ejecutar=true").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            ctx.getBean(org.springframework.boot.ApplicationRunner.class).run(null);
        });
        verify(mongo).remove(any(Query.class), eq("tiendas"));
    }

    @Test
    void conElIndicadorYLaSimulacionElRunnerSoloInforma() {
        contexto().withPropertyValues("migracion.limpieza-tienda-propia.ejecutar=true", "migracion.limpieza-tienda-propia.simular=true")
                .run(ctx -> ctx.getBean(org.springframework.boot.ApplicationRunner.class).run(null));
        verificarQueNoSeBorroNada();
    }

    @Test
    void ambosIndicadoresVivenApagadosPorDefectoEnLaConfiguracion() throws Exception {
        var props = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        assertEquals("${MIGRAR_LIMPIEZA_TIENDA_PROPIA:false}", props.getProperty("migracion.limpieza-tienda-propia.ejecutar"));
        assertEquals("${MIGRAR_LIMPIEZA_TIENDA_PROPIA_SIMULAR:false}", props.getProperty("migracion.limpieza-tienda-propia.simular"));
    }
}
