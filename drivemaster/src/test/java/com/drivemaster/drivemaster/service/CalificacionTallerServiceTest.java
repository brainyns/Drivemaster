package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.service.CalificacionTallerService.Resumen;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La calificación del taller es el promedio real de las notas de los clientes, nunca un 5.0 por defecto. */
class CalificacionTallerServiceTest {

    private OrdenServicioRepository ordenes;
    private TallerRepository talleres;
    private MongoTemplate mongo;
    private CalificacionTallerService servicio;

    @BeforeEach
    void setUp() {
        ordenes = mock(OrdenServicioRepository.class);
        talleres = mock(TallerRepository.class);
        mongo = mock(MongoTemplate.class);
        servicio = new CalificacionTallerService(ordenes, talleres, mongo);
    }

    private static OrdenServicio calificada(String tallerId, Integer nota) {
        return OrdenServicio.builder().tallerId(tallerId).calificacionTaller(nota).build();
    }

    @Test
    void promedioConUnDecimalYCantidad() {
        assertEquals(new Resumen(4.3, 3), CalificacionTallerService.resumir(List.of(5, 4, 4)));
        assertEquals(new Resumen(4.5, 2), CalificacionTallerService.resumir(List.of(5, 4)));
        assertEquals(new Resumen(1.0, 1), CalificacionTallerService.resumir(List.of(1)));
    }

    @Test
    void sinCalificacionesNoHayPromedioNiUnCincoFalso() {
        Resumen vacio = CalificacionTallerService.resumir(List.of());
        assertNull(vacio.promedio());
        assertEquals(0, vacio.total());
        assertEquals(Resumen.SIN_CALIFICACIONES, CalificacionTallerService.resumir(null));
    }

    @Test
    void lasNotasFueraDeRangoONulasNoCuentan() {
        assertEquals(new Resumen(3.0, 2), CalificacionTallerService.resumir(Arrays.asList(2, 4, 0, 6, null)));
    }

    @Test
    void recalcularGuardaElPromedioYLaCantidadEnElTaller() {
        when(ordenes.findByTallerIdAndCalificacionTallerNotNull("t1"))
                .thenReturn(List.of(calificada("t1", 5), calificada("t1", 3)));

        assertEquals(new Resumen(4.0, 2), servicio.recalcular("t1"));

        ArgumentCaptor<Update> cambio = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), cambio.capture(), eq(Taller.class));
        Document set = (Document) cambio.getValue().getUpdateObject().get("$set");
        assertEquals(4.0, set.get("rating"));
        assertEquals(2, set.get("calificaciones"));
    }

    @Test
    void elRecalculoDeArranqueQuitaElCincoFalsoYNoReescribeLoQueYaEstaAlDia() {
        Taller falso = Taller.builder().id("nuevo").rating(5.0).build();
        Taller alDia = Taller.builder().id("al-dia").rating(4.5).calificaciones(2).build();
        Taller desfasado = Taller.builder().id("desfasado").rating(5.0).calificaciones(1).build();
        when(talleres.findAll()).thenReturn(List.of(falso, alDia, desfasado));
        when(ordenes.findByCalificacionTallerNotNull()).thenReturn(List.of(
                calificada("al-dia", 5), calificada("al-dia", 4),
                calificada("desfasado", 5), calificada("desfasado", 2)));

        assertEquals(2, servicio.recalcularTodos());

        ArgumentCaptor<Update> cambios = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(any(Query.class), cambios.capture(), eq(Taller.class));
        Document sinNotas = (Document) cambios.getAllValues().get(0).getUpdateObject().get("$set");
        assertNull(sinNotas.get("rating"), "un taller sin calificaciones queda sin promedio");
        assertEquals(0, sinNotas.get("calificaciones"));
        Document recalculado = (Document) cambios.getAllValues().get(1).getUpdateObject().get("$set");
        assertEquals(3.5, recalculado.get("rating"));
        assertEquals(2, recalculado.get("calificaciones"));
    }
}
