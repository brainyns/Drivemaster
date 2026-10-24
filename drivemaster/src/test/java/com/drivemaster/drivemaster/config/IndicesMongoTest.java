package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.HorarioTaller;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndicesMongoTest {

    private MongoTemplate mongo;
    private IndexOperations ordenes;
    private IndexOperations horarios;
    private IndexOperations usuarios;
    private IndicesMongo indices;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        ordenes = mock(IndexOperations.class);
        horarios = mock(IndexOperations.class);
        usuarios = mock(IndexOperations.class);
        when(mongo.indexOps(OrdenServicio.class)).thenReturn(ordenes);
        when(mongo.indexOps(HorarioTaller.class)).thenReturn(horarios);
        when(mongo.indexOps(Usuario.class)).thenReturn(usuarios);
        when(mongo.indexOps(com.drivemaster.drivemaster.model.CambioTaller.class)).thenReturn(mock(IndexOperations.class));
        indices = spy(new IndicesMongo(mongo));
    }

    @Test
    void creaLosIndicesDeLasConsultasRealesYElCorreoUnico() {
        doReturn(List.of()).when(indices).correosDuplicados(any(Boolean.class));
        indices.run(null);

        verify(ordenes, times(5)).ensureIndex(any());
        verify(horarios).ensureIndex(any());
        ArgumentCaptor<IndexDefinition> correo = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(usuarios).ensureIndex(correo.capture());
        assertEquals(Boolean.TRUE, correo.getValue().getIndexOptions().get("unique"));
        assertTrue(correo.getValue().getIndexOptions().containsKey("partialFilterExpression"), "las cuentas sin correo no chocan");
    }

    @Test
    void conCorreosRepetidosNoCreaElIndiceUnicoYArrancaIgual() {
        doReturn(List.of("ana@demo.com")).when(indices).correosDuplicados(false);
        assertDoesNotThrow(() -> indices.run(null));
        verify(usuarios, never()).ensureIndex(any());
        verify(ordenes, times(5)).ensureIndex(any());
    }

    @Test
    void siUnaColeccionFallaLasDemasSiguen() {
        doThrow(new RuntimeException("sin permisos")).when(ordenes).ensureIndex(any());
        doReturn(List.of()).when(indices).correosDuplicados(any(Boolean.class));
        assertDoesNotThrow(() -> indices.run(null));
        verify(horarios).ensureIndex(any());
        verify(usuarios).ensureIndex(any());
    }

    @Test
    void elLogNoGuardaElCorreoCompleto() {
        assertEquals("ju***@gmail.com", IndicesMongo.enmascarar("juan.perez@gmail.com"));
        assertEquals("a***@x.co", IndicesMongo.enmascarar("a@x.co"));
        assertEquals("***", IndicesMongo.enmascarar("sin-arroba"));
    }
}
