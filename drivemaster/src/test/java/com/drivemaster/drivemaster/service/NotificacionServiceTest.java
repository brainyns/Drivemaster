package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Notificacion;
import com.drivemaster.drivemaster.repository.NotificacionRepository;
import com.drivemaster.drivemaster.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Los avisos se guardan, se envían solo a su dueño y nunca rompen el flujo que los origina. */
class NotificacionServiceTest {

    private NotificacionRepository repository;
    private MongoTemplate mongo;
    private NotificationWebSocketHandler handler;
    private NotificacionService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificacionRepository.class);
        mongo = mock(MongoTemplate.class);
        handler = mock(NotificationWebSocketHandler.class);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new NotificacionService(repository, mongo, handler, json);
        when(repository.save(any(Notificacion.class))).thenAnswer(i -> {
            Notificacion n = i.getArgument(0);
            n.setId("n1");
            return n;
        });
        when(repository.countByUsuarioIdAndLeidaFalse("u1")).thenReturn(3L);
    }

    private void avisarAU1() {
        service.avisar("u1", Notificacion.PEDIDO_NUEVO, "Nuevo pedido pagado", "Pedido de Taller Norte", "pedidos-tienda", "p1");
    }

    @Test
    void seGuardaYSeEnviaSoloASuDueno() {
        avisarAU1();

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(repository).save(guardada.capture());
        assertEquals("u1", guardada.getValue().getUsuarioId());
        assertFalse(guardada.getValue().isLeida());
        assertNotNull(guardada.getValue().getFechaCreacion());

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(handler).enviarAUsuario(eq("u1"), enviado.capture());
        assertTrue(enviado.getValue().contains("\"type\":\"notificacion\""));
        assertTrue(enviado.getValue().contains("\"noLeidas\":3"));
        assertTrue(enviado.getValue().contains("\"enlace\":\"pedidos-tienda\""));
        assertFalse(enviado.getValue().contains("usuarioId"), "el id del dueño no viaja en el aviso");
        verify(handler, never()).enviarARoles(any(), anyString());
    }

    @Test
    void sinDestinatarioNoHaceNada() {
        service.avisar(null, Notificacion.PEDIDO_NUEVO, "t", "m", null, null);
        service.avisar(" ", Notificacion.PEDIDO_NUEVO, "t", "m", null, null);

        verifyNoInteractions(repository, handler);
    }

    @Test
    void siMongoFallaNoSeLanzaNadaNiSeEnvia() {
        when(repository.save(any(Notificacion.class))).thenThrow(new RuntimeException("Mongo caído"));

        assertDoesNotThrow(this::avisarAU1);

        verify(handler, never()).enviarAUsuario(anyString(), anyString());
    }

    @Test
    void siElEnvioFallaNoSeLanzaNada() {
        doThrow(new RuntimeException("socket roto")).when(handler).enviarAUsuario(anyString(), anyString());

        assertDoesNotThrow(this::avisarAU1);

        verify(repository).save(any(Notificacion.class));
    }

    @Test
    void dentroDeUnaTransaccionEsperaAQueSeConfirme() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            avisarAU1();
            verifyNoInteractions(repository, handler);

            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) s.afterCommit();

            verify(repository).save(any(Notificacion.class));
            verify(handler).enviarAUsuario(eq("u1"), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void siLaTransaccionSeRevierteNoSaleElAviso() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            avisarAU1();
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verifyNoInteractions(repository, handler);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void elLimiteDeLaListaSeAcota() {
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);

        service.mias("u1", null);
        service.mias("u1", 0);
        service.mias("u1", 5000);

        verify(repository, times(3)).findByUsuarioIdOrderByFechaCreacionDesc(eq("u1"), pagina.capture());
        assertEquals(NotificacionService.LIMITE_POR_DEFECTO, pagina.getAllValues().get(0).getPageSize());
        assertEquals(1, pagina.getAllValues().get(1).getPageSize());
        assertEquals(NotificacionService.LIMITE_MAXIMO, pagina.getAllValues().get(2).getPageSize());
    }

    @Test
    void marcarLeidaFiltraPorDuenoYDevuelveFalseSiNoEsSuya() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Notificacion.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertFalse(service.marcarLeida("u1", "n-de-otro"));

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(q.capture(), any(Update.class), eq(Notificacion.class));
        assertEquals("u1", q.getValue().getQueryObject().get("usuarioId"));
        assertEquals("n-de-otro", q.getValue().getQueryObject().get("_id"));
    }

    @Test
    void marcarTodasSoloTocaLasDelUsuario() {
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Notificacion.class)))
                .thenReturn(UpdateResult.acknowledged(4, 4L, null));

        assertEquals(4, service.marcarTodasLeidas("u1"));

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateMulti(q.capture(), any(Update.class), eq(Notificacion.class));
        assertEquals("u1", q.getValue().getQueryObject().get("usuarioId"));
        assertEquals(false, q.getValue().getQueryObject().get("leida"));
    }
}
