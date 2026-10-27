package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO.Pedidos;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;

/** Tablas paginadas de la tienda: las mismas reglas que antes aplicaba el navegador, ahora en el servidor. */
class TablasTiendaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 10, 0);

    private MongoTemplate mongo;
    private TablasTiendaService servicio;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class, RETURNS_DEEP_STUBS);
        servicio = new TablasTiendaService(mongo);
    }

    private static PedidoTienda pedido(String id, String estado) {
        return PedidoTienda.builder().id(id).tiendaId("t1").estado(estado).origen(PedidoTienda.ORIGEN_CATALOGO)
                .entrega(PedidoTienda.ENVIO).fechaCreacion(AHORA.minusDays(1)).total(10000.0).build();
    }

    // ─── Pedidos ────────────────────────────────────────────────────────────

    @Test
    void laBusquedaDePedidosNoRevelaElApellidoCompletoDeUnRetiro() {
        PedidoTienda retiro = pedido("66f0a1b2c3d4e5f6a7b8c9d0", PedidoTienda.LISTO_PARA_RETIRAR).toBuilder()
                .entrega(PedidoTienda.RETIRO).clienteNombre("Laura Martínez").build();
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of(retiro));
        PeticionTabla t = TablasTiendaService.peticionPedidos(0, 20, null);

        assertEquals(0, servicio.pedidos("t1", "TODOS", "martínez", RangoFechas.SIN_RANGO, t, AHORA).pedidos().total(),
                "la tienda solo ve «Laura M.»: buscar el apellido no lo confirma");
        assertEquals(1, servicio.pedidos("t1", "TODOS", "laura", RangoFechas.SIN_RANGO, t, AHORA).pedidos().total());
        // Por referencia, con o sin #
        assertEquals(1, servicio.pedidos("t1", "TODOS", "#B8C9D0", RangoFechas.SIN_RANGO, t, AHORA).pedidos().total());
    }

    @Test
    void laBusquedaTambienMiraProductosTallerYVehiculo() {
        CotizacionItem item = new CotizacionItem();
        item.setProductoNombre("Pastillas Brembo");
        PedidoTienda p = pedido("p1", PedidoTienda.PENDIENTE).toBuilder().items(List.of(item)).tallerNombre("AutoMax")
                .vehiculoDescripcion("Mazda 2").build();

        for (String q : new String[]{"brembo", "automax", "MAZDA"}) assertTrue(TablasTiendaService.coincidePedido(q).test(p), q);
        assertFalse(TablasTiendaService.coincidePedido("bosch").test(p));
        assertTrue(TablasTiendaService.coincidePedido("  ").test(p));
    }

    @Test
    void lasPestanasYElRangoFiltranEnLaBase() {
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of());

        servicio.pedidos("t1", "entregados", null, RangoFechas.de("2026-10-01", "2026-10-07"),
                TablasTiendaService.peticionPedidos(0, 20, null), AHORA);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(PedidoTienda.class));
        String filtro = q.getValue().getQueryObject().toString();
        assertTrue(filtro.contains("tiendaId=t1") && filtro.contains("RETIRADO") && filtro.contains("fechaCreacion"), filtro);
        assertTrue(filtro.contains("2026-10-08T00:00"), "hasta incluye todo el 7: " + filtro);
    }

    @Test
    void unaPestanaInventadaResponde400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.pedidos("t1", "BORRADOS", null,
                RangoFechas.SIN_RANGO, TablasTiendaService.peticionPedidos(0, 20, null), AHORA));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void sinSortVanPrimeroLosVencidosLuegoPorDespacharLuegoListosYAlFinalElResto() {
        PedidoTienda vencido = pedido("a", PedidoTienda.PENDIENTE).toBuilder().plazoDespachoHasta(AHORA.minusHours(1)).build();
        PedidoTienda porDespachar = pedido("b", PedidoTienda.EN_PREPARACION).toBuilder().plazoDespachoHasta(AHORA.plusDays(1)).build();
        PedidoTienda listoViejo = pedido("c", PedidoTienda.LISTO_PARA_RETIRAR).toBuilder().fechaListoRetiro(AHORA.minusDays(5)).build();
        PedidoTienda listoNuevo = pedido("d", PedidoTienda.LISTO_PARA_RETIRAR).toBuilder().fechaListoRetiro(AHORA.minusDays(1)).build();
        PedidoTienda entregado = pedido("e", PedidoTienda.ENTREGADO).toBuilder().fechaCreacion(AHORA.minusDays(9)).build();
        PedidoTienda entregadoReciente = pedido("f", PedidoTienda.ENTREGADO).toBuilder().fechaCreacion(AHORA.minusDays(2)).build();
        List<PedidoTienda> desordenados = new ArrayList<>(List.of(entregado, listoNuevo, porDespachar, entregadoReciente, vencido, listoViejo));

        desordenados.sort(TablasTiendaService.ordenPedidos(TablasTiendaService.peticionPedidos(0, 20, null), AHORA));

        assertEquals(List.of("a", "b", "c", "d", "f", "e"), desordenados.stream().map(PedidoTienda::getId).toList());
    }

    @Test
    void conSortOrdenaPorEseCampoEnAmbasDirecciones() {
        List<PedidoTienda> pedidos = new ArrayList<>(List.of(
                pedido("a", PedidoTienda.PENDIENTE).toBuilder().total(30000.0).build(),
                pedido("b", PedidoTienda.PENDIENTE).toBuilder().total(5000.0).build(),
                pedido("c", PedidoTienda.PENDIENTE).toBuilder().total(12000.0).build()));

        pedidos.sort(TablasTiendaService.ordenPedidos(TablasTiendaService.peticionPedidos(0, 20, "total,asc"), AHORA));
        assertEquals(List.of("b", "c", "a"), pedidos.stream().map(PedidoTienda::getId).toList());
        pedidos.sort(TablasTiendaService.ordenPedidos(TablasTiendaService.peticionPedidos(0, 20, "total,desc"), AHORA));
        assertEquals(List.of("a", "c", "b"), pedidos.stream().map(PedidoTienda::getId).toList());

        assertThrows(ResponseStatusException.class, () -> TablasTiendaService.peticionPedidos(0, 20, "clienteNombre,asc"));
    }

    @Test
    void laPaginaDePedidosTraeElTotalDeTodosLosQueCoinciden() {
        List<PedidoTienda> muchos = IntStream.range(0, 45).mapToObj(i -> pedido(String.format("p%02d", i), PedidoTienda.ENTREGADO)).toList();
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(muchos);

        Pedidos r = servicio.pedidos("t1", "TODOS", null, RangoFechas.SIN_RANGO, TablasTiendaService.peticionPedidos(2, 20, null), AHORA);

        assertEquals(45, r.pedidos().total());
        assertEquals(3, r.pedidos().totalPaginas());
        assertEquals(5, r.pedidos().items().size());
        // Lo que ve la tienda: sin identificadores del cliente
        assertTrue(r.pedidos().items().stream().allMatch(p -> p.getClienteId() == null && p.getPagoId() == null));
        assertEquals(List.of("ATENDER", "PREPARACION", "LISTOS", "EN_CAMINO", "NUEVOS", "ENTREGADOS", "CANCELADOS", "TODOS"),
                List.copyOf(r.conteo().keySet()));
    }

    // ─── Movimientos ────────────────────────────────────────────────────────

    @Test
    void movimientosFiltraPorTipoProductoYFechasEnLaBaseYPagina() {
        Producto p = new Producto();
        p.setId("prod1");
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(p));
        when(mongo.count(any(Query.class), eq(MovimientoInventario.class))).thenReturn(103L);
        when(mongo.find(any(Query.class), eq(MovimientoInventario.class))).thenReturn(List.of(new MovimientoInventario()));

        var r = servicio.movimientos("t1", "salida", "freno", RangoFechas.de("2026-10-01", null),
                TablasTiendaService.peticionMovimientos(9, 20, "cantidad,desc"));

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(MovimientoInventario.class));
        String filtro = q.getValue().getQueryObject().toString();
        assertTrue(filtro.contains("tiendaId=t1") && filtro.contains("SALIDA") && filtro.contains("prod1") && filtro.contains("fecha"), filtro);
        assertEquals(5, q.getValue().getSkip() / 20, "page 9 de 6 páginas: la última");
        assertTrue(q.getValue().getSortObject().toString().contains("cantidad=-1"));
        assertEquals(103, r.movimientos().total());
        assertEquals(6, r.movimientos().totalPaginas());
    }

    @Test
    void unTipoDeMovimientoInventadoResponde400() {
        assertThrows(ResponseStatusException.class, () -> servicio.movimientos("t1", "ROBO", null, RangoFechas.SIN_RANGO,
                TablasTiendaService.peticionMovimientos(0, 20, null)));
        assertThrows(ResponseStatusException.class, () -> TablasTiendaService.peticionMovimientos(0, 20, "motivo,asc"));
    }

    // ─── Inventario ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void elInventarioFiltraBuscaOrdenaYPaginaEnLaBase() {
        servicio.inventario("t1", "poco", "freno", TablasTiendaService.peticionInventario(1, 20, "precio,desc"));

        ArgumentCaptor<List<Document>> etapas = ArgumentCaptor.forClass(List.class);
        verify(mongo.getCollection("productos"), org.mockito.Mockito.atLeastOnce()).aggregate(etapas.capture());
        // La última consulta pedida es la de los indicadores; la de la página es la que trae $skip
        List<Document> pagina = etapas.getAllValues().stream()
                .filter(l -> l.stream().anyMatch(d -> d.containsKey("$skip"))).findFirst().orElseThrow();
        String texto = pagina.toString();
        assertTrue(texto.contains("tiendaId=t1") && texto.contains("freno"), texto);
        assertTrue(texto.contains("_nivel=bajo"), "poco stock es el nivel bajo: " + texto);
        assertTrue(texto.contains("precioVenta=-1"), texto);
        assertTrue(texto.contains("$limit=20"), texto);
    }

    @Test
    void elFiltroDelInventarioYElOrdenTienenListaBlanca() {
        assertThrows(ResponseStatusException.class,
                () -> servicio.inventario("t1", "CAROS", null, TablasTiendaService.peticionInventario(0, 20, null)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> TablasTiendaService.peticionInventario(0, 20, "precioCompra,desc"));
        assertTrue(e.getReason().contains("estado, nombre, precio, stock"), e.getReason());
    }

    @Test
    void elOrdenPorDefectoDelInventarioEsLoQuePideAtencionPrimeroYLuegoElNombre() {
        Document orden = TablasTiendaService.aDocumento(TablasTiendaService.peticionInventario(0, 20, null).orden());
        assertEquals(List.of("_peso", "nombre", "_id"), List.copyOf(orden.keySet()));
        assertEquals(1, orden.get("_peso"));
    }

    @Test
    void elNivelDeStockEsElMismoDelNavegador_margenDeAlMenos2OLaMitadDelMinimo() {
        String nivel = TablasTiendaService.nivelStock().toJson();
        for (String pieza : new String[]{"ENCARGO", "agotado", "bajo", "cerca", "$stockMinimo", "0.5", "$ceil", "missing"}) {
            assertTrue(nivel.contains(pieza), pieza + " en " + nivel);
        }
    }
}
