package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.plataforma.ConsultasPlataformaDTO.PedidosRespuesta;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.dispersion.DispersionService;

/** Tablas paginadas del SUPERADMIN: orden con lista blanca, rango de fechas y el formato común en el servidor. */
class TablasAdminTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 10, 0);

    private MongoTemplate mongo;
    private PlataformaConsultasService consultas;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mongo = mock(MongoTemplate.class);
        consultas = new PlataformaConsultasService(mongo);
        AggregationResults<Document> vacio = mock(AggregationResults.class);
        when(vacio.getMappedResults()).thenReturn(List.of());
        when(mongo.aggregate(any(Aggregation.class), any(String.class), eq(Document.class))).thenReturn(vacio);
        when(mongo.aggregate(any(Aggregation.class), any(Class.class), eq(Document.class))).thenReturn(vacio);
        when(mongo.findAll(Tienda.class)).thenReturn(List.of());
    }

    private Query consultaDe(Class<?> tipo) {
        ArgumentCaptor<Query> c = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(c.capture(), eq(tipo));
        return c.getValue();
    }

    @Test
    void elMonitorDePedidosFiltraPorFechaDePagoYOrdenaPorElCampoPedido() {
        when(mongo.count(any(Query.class), eq(PedidoTienda.class))).thenReturn(45L);
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of());

        PedidosRespuesta r = consultas.pedidos(null, null, null, null, RangoFechas.de("2026-10-01", "2026-10-31"),
                PeticionTabla.de(1, 20, null, null, "tienda,asc", PlataformaConsultasService.ORDEN_PEDIDOS), AHORA);

        Query q = consultaDe(PedidoTienda.class);
        String filtro = q.getQueryObject().toString();
        assertTrue(filtro.contains("fechaCreacion") && filtro.contains("2026-10-01T00:00") && filtro.contains("2026-11-01T00:00"), filtro);
        assertTrue(q.getSortObject().toString().contains("tiendaNombre=1"), q.getSortObject().toString());
        assertEquals(20, q.getSkip());
        assertEquals(45, r.pedidos().total());
        assertEquals(3, r.pedidos().totalPaginas());
        assertEquals(1, r.pedidos().pagina());
    }

    @Test
    void sinSortElMonitorSigueDelMasRecienteYLosAliasViejosFuncionan() {
        when(mongo.count(any(Query.class), eq(PedidoTienda.class))).thenReturn(5L);
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of());

        // pagina/tamano: la firma de antes (y lo que siguen mandando los alias)
        consultas.pedidos(null, null, null, false, 0, 20);

        assertTrue(consultaDe(PedidoTienda.class).getSortObject().toString().contains("fechaCreacion=-1"));
    }

    @Test
    void productosClientesYUsuariosOrdenanPorSuListaBlanca() {
        when(mongo.count(any(Query.class), eq(Producto.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(Producto.class))).thenReturn(List.of());
        consultas.productos(null, null, null, null, PeticionTabla.de(0, 20, null, null, "precio,desc", PlataformaConsultasService.ORDEN_PRODUCTOS));
        assertTrue(consultaDe(Producto.class).getSortObject().toString().contains("precioVenta=-1"));

        when(mongo.count(any(Query.class), eq(Usuario.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of());
        consultas.clientes(null, null, null, PeticionTabla.de(0, 20, null, null, "nombre,asc", PlataformaConsultasService.ORDEN_CLIENTES));
        assertTrue(consultaDe(Usuario.class).getSortObject().toString().contains("nombre=1"));
    }

    @Test
    void camposQueNoEstanEnLaListaBlancaDeCadaTablaResponden400() {
        assertThrows(ResponseStatusException.class, () -> PeticionTabla.de(0, 20, null, null, "correo,asc", PlataformaConsultasService.ORDEN_CLIENTES));
        assertThrows(ResponseStatusException.class, () -> PeticionTabla.de(0, 20, null, null, "password,asc", PlataformaConsultasService.ORDEN_USUARIOS));
        assertThrows(ResponseStatusException.class, () -> PeticionTabla.de(0, 20, null, null, "precioCompra", PlataformaConsultasService.ORDEN_PRODUCTOS));
        assertThrows(ResponseStatusException.class, () -> PeticionTabla.de(0, 20, null, null, "envioDireccion", PlataformaConsultasService.ORDEN_PEDIDOS));
        assertThrows(ResponseStatusException.class, () -> PeticionTabla.de(0, 20, null, null, "nota,desc", LiquidacionService.ORDEN_REPARTOS));
    }

    // ─── Liquidaciones ──────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void lasLiquidacionesPaginanEnLaBaseYElPendienteCuentaTodaLaListaFiltrada() {
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        LiquidacionService servicio = new LiquidacionService(repartos, mock(ReembolsoRepository.class), mock(PagoRepository.class),
                mock(DispersionService.class), mock(AvisosSolicitudServicio.class));
        RepartoPago pendiente = RepartoPago.builder().id(1L).estado(RepartoPago.POR_LIQUIDAR).monto(50_000.0).build();
        RepartoPago otro = RepartoPago.builder().id(2L).estado(RepartoPago.FALLIDO).monto(20_000.0).build();
        when(repartos.count(any(Specification.class))).thenReturn(41L);
        when(repartos.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pendiente)));
        when(repartos.findAll(any(Specification.class))).thenReturn(List.of(pendiente, otro));

        LiquidacionService.RepartosTabla r = servicio.repartosTabla("", RangoFechas.de("2026-10-01", null),
                PeticionTabla.de(5, 20, null, null, "monto,desc", LiquidacionService.ORDEN_REPARTOS));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(repartos).findAll(any(Specification.class), pagina.capture());
        assertEquals(2, pagina.getValue().getPageNumber(), "page 5 de 3 páginas: la última");
        assertEquals("monto: DESC,id: DESC", pagina.getValue().getSort().toString());
        assertEquals(41, r.repartos().total());
        assertEquals(3, r.repartos().totalPaginas());
        assertEquals(2, r.pendientes());
        assertEquals(70_000.0, r.montoPendiente());
    }
}
