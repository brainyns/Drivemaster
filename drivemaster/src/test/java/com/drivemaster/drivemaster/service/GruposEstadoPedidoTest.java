package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Tienda;

/** Las pestañas agrupadas de Pedidos y envíos: el parámetro estado acepta también un grupo de estados. */
class GruposEstadoPedidoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 10, 0);
    private static final Set<String> TODOS_LOS_ESTADOS = Set.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION,
            PedidoTienda.DESPACHADO, PedidoTienda.NO_DESPACHADO, PedidoTienda.RECIBIDO, PedidoTienda.LISTO_PARA_RETIRAR,
            PedidoTienda.RETIRADO, PedidoTienda.ENTREGADO, PedidoTienda.NO_ENTREGADO);

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
        when(mongo.findAll(Tienda.class)).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of());
    }

    private String filtroDe(String estado) {
        consultas.pedidos(estado, null, null, null, RangoFechas.SIN_RANGO,
                PeticionTabla.de(0, 20, null, null, null, PlataformaConsultasService.ORDEN_PEDIDOS), AHORA);
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(PedidoTienda.class));
        return q.getValue().getQueryObject().toString();
    }

    @Test
    void porDespacharSonPendientesYEnPreparacion() {
        String f = filtroDe("por_despachar");
        assertTrue(f.contains("$in") && f.contains("PENDIENTE") && f.contains("EN_PREPARACION") && !f.contains("DESPACHADO,"), f);
    }

    @Test
    void enCaminoEsDespachadoYCompletadosSonRecibidoRetiradoYEntregado() {
        assertTrue(filtroDe("EN_CAMINO").contains("DESPACHADO"));
    }

    @Test
    void completados() {
        String f = filtroDe("COMPLETADOS");
        assertTrue(f.contains("RECIBIDO") && f.contains("RETIRADO") && f.contains("ENTREGADO") && !f.contains("NO_ENTREGADO"), f);
    }

    @Test
    void canceladosSonNoDespachadoYNoEntregado() {
        String f = filtroDe("CANCELADOS");
        assertTrue(f.contains("NO_DESPACHADO") && f.contains("NO_ENTREGADO"), f);
    }

    @Test
    void losEstadosSueltosYElProblemaDeEnvioSiguenFuncionando() {
        assertTrue(filtroDe("LISTO_PARA_RETIRAR").contains("estado=LISTO_PARA_RETIRAR"));
    }

    @Test
    void elProblemaDeEnvioAbiertoNoEsElGrupoCancelados() {
        String f = filtroDe("PROBLEMA");
        assertTrue(f.contains("problemaReportado") && !f.contains("NO_DESPACHADO"), f);
    }

    @Test
    void unGrupoInventadoNoDevuelveNada() {
        assertTrue(filtroDe("ARCHIVADOS").contains("__ninguno__"));
    }

    @Test
    void entreTodosLosGruposCubrenLosNueveEstadosSinRepetir() {
        List<String> todos = new ArrayList<>();
        PlataformaConsultasService.GRUPOS_PEDIDO.values().forEach(todos::addAll);
        assertEquals(TODOS_LOS_ESTADOS, new HashSet<>(todos));
        assertEquals(TODOS_LOS_ESTADOS.size(), todos.size(), "ningún estado está en dos grupos");
    }
}
