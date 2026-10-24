package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO;
import com.drivemaster.drivemaster.model.DiaSemana;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.HorarioTaller;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.CambioTallerRepository;
import com.drivemaster.drivemaster.repository.HorarioTallerRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HorarioTallerServiceTest {

    private HorarioTallerRepository horarios;
    private TallerRepository tallerRepository;
    private HorarioTallerService servicio;
    private Taller a;

    @BeforeEach
    void setUp() {
        horarios = mock(HorarioTallerRepository.class);
        tallerRepository = mock(TallerRepository.class);
        TallerService tallerService = mock(TallerService.class);
        servicio = new HorarioTallerService(horarios, tallerRepository, tallerService, mock(CambioTallerRepository.class));
        a = Taller.builder().id("A").estadoAprobacion(EstadoAprobacion.APROBADO).horario("L-V 8-18").build();
        when(tallerService.obtenerPorUsuario("a@demo.com")).thenReturn(a);
    }

    private static FranjaHorarioDTO abierto(DiaSemana dia, String desde, String hasta) {
        return FranjaHorarioDTO.builder().diaSemana(dia).abierto(true)
                .horaApertura(LocalTime.parse(desde)).horaCierre(LocalTime.parse(hasta)).build();
    }

    private static FranjaHorarioDTO cerrado(DiaSemana dia) {
        return FranjaHorarioDTO.builder().diaSemana(dia).abierto(false).build();
    }

    private static int estado(ResponseStatusException e) {
        return e.getStatusCode().value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reemplazaElHorarioCompletoConFranjasOrdenadasYDiasFaltantesCerrados() {
        servicio.reemplazar("A", "a@demo.com", List.of(
                abierto(DiaSemana.LUNES, "14:00", "18:00"), abierto(DiaSemana.LUNES, "08:00", "12:00"),
                abierto(DiaSemana.MARTES, "08:00", "12:00"), abierto(DiaSemana.MARTES, "14:00", "18:00"),
                cerrado(DiaSemana.DOMINGO)));

        verify(horarios).deleteByTallerId("A");
        ArgumentCaptor<List<HorarioTaller>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(horarios).saveAll(guardadas.capture());
        List<HorarioTaller> filas = guardadas.getValue();
        assertEquals(4 + 5, filas.size(), "4 franjas y 5 días cerrados (miércoles a domingo)");
        HorarioTaller primera = filas.get(0);
        assertEquals(LocalTime.of(8, 0), primera.getHoraApertura(), "las franjas del día van de la más temprana");
        assertEquals(0, primera.getOrden());
        assertTrue(filas.stream().allMatch(f -> "A".equals(f.getTallerId())));
        assertFalse(filas.stream().filter(f -> f.getDiaSemana() == DiaSemana.SABADO).findFirst().orElseThrow().isAbierto());

        assertEquals("Lun y Mar 8:00 a. m.–12:00 p. m. y 2:00 p. m.–6:00 p. m.; Mié a Dom cerrado", a.getHorario(),
                "el texto que ve el cliente se regenera");
    }

    @Test
    void rechazaCierreAntesDeAperturaYFranjasQueSeCruzan() {
        var e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar("A", "a@demo.com",
                List.of(abierto(DiaSemana.LUNES, "18:00", "08:00"))));
        assertEquals(400, estado(e));
        assertEquals("Lunes: la hora de cierre debe ser después de la de apertura", e.getReason());

        e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar("A", "a@demo.com",
                List.of(abierto(DiaSemana.MIERCOLES, "08:00", "12:30"), abierto(DiaSemana.MIERCOLES, "12:00", "18:00"))));
        assertEquals("Miércoles: las franjas se cruzan", e.getReason());

        // Que una termine justo cuando empieza la otra no es cruce
        servicio.reemplazar("A", "a@demo.com",
                List.of(abierto(DiaSemana.JUEVES, "08:00", "12:00"), abierto(DiaSemana.JUEVES, "12:00", "18:00")));
    }

    @Test
    void unTallerNoVeNiCambiaElHorarioDeOtro() {
        var e = assertThrows(ResponseStatusException.class, () -> servicio.obtener("B", "a@demo.com", false));
        assertEquals(403, estado(e));
        e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar("B", "a@demo.com", List.of()));
        assertEquals(403, estado(e));
        verify(horarios, never()).deleteByTallerId(anyString());
    }

    @Test
    void sinAprobacionNoSeGuarda() {
        a.setEstadoAprobacion(EstadoAprobacion.PENDIENTE);
        var e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar("A", "a@demo.com", List.of()));
        assertEquals(403, estado(e));
    }

    @Test
    void horaEnFormatoDoceHoras() {
        assertEquals("12:00 p. m.", HorarioTallerService.hora12(LocalTime.NOON));
        assertEquals("12:30 a. m.", HorarioTallerService.hora12(LocalTime.of(0, 30)));
        assertEquals("6:00 p. m.", HorarioTallerService.hora12(LocalTime.of(18, 0)));
    }
}
