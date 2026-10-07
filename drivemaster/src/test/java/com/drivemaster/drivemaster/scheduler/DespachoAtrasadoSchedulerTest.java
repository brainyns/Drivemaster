package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.service.AvisosSolicitudServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DespachoAtrasadoSchedulerTest {

    private MongoTemplate mongoTemplate;
    private AvisosSolicitudServicio avisos;
    private DespachoAtrasadoScheduler scheduler;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        avisos = mock(AvisosSolicitudServicio.class);
        scheduler = new DespachoAtrasadoScheduler(mongoTemplate, avisos);
    }

    private static PedidoTienda pedido(String id) {
        return PedidoTienda.builder().id(id).tiendaNombre("Tienda Norte").estado(PedidoTienda.PENDIENTE)
                .plazoDespachoHasta(LocalDateTime.now().minusHours(1)).total(71_400.0).build();
    }

    @Test
    void alertaUnaVezPorCadaPedidoVencido() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(pedido("p1"), pedido("p2"), null);

        assertEquals(2, scheduler.alertarAtrasos(LocalDateTime.now()));

        verify(avisos, times(2)).despachoAtrasado(any(PedidoTienda.class));
    }

    @Test
    void sinPedidosVencidosNoAlerta() {
        assertEquals(0, scheduler.alertarAtrasos(LocalDateTime.now()));
        verifyNoInteractions(avisos);
    }

    @Test
    void elFiltroSoloTomaPedidosSinDespacharYSinAlertaPrevia() {
        scheduler.alertarAtrasos(LocalDateTime.now());

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(q.capture(), u.capture(), any(FindAndModifyOptions.class), eq(PedidoTienda.class));
        String filtro = q.getValue().getQueryObject().toString();

        // Pendiente o en preparación: un pedido NO_DESPACHADO, DESPACHADO o RECIBIDO ya no alerta
        assertTrue(filtro.contains(PedidoTienda.PENDIENTE) && filtro.contains(PedidoTienda.EN_PREPARACION), filtro);
        assertFalse(filtro.contains(PedidoTienda.NO_DESPACHADO) || filtro.contains(PedidoTienda.DESPACHADO), filtro);
        // Plazo vencido y una sola alerta por pedido
        assertTrue(filtro.contains("plazoDespachoHasta") && filtro.contains("$lt"), filtro);
        assertTrue(filtro.contains("alertaAtrasoEnviada") && filtro.contains("$ne"), filtro);
        // El mismo findAndModify marca la alerta como enviada (reclamo atómico)
        assertTrue(u.getValue().getUpdateObject().toString().contains("alertaAtrasoEnviada"));
    }
}
