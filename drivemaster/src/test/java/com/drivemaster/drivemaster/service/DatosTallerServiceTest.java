package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.taller.ActualizarDatosTallerRequest;
import com.drivemaster.drivemaster.model.CambioTaller;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.CambioTallerRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatosTallerServiceTest {

    private TallerService tallerService;
    private TallerRepository tallerRepository;
    private CambioTallerRepository cambios;
    private DatosTallerService servicio;
    private Taller a;

    @BeforeEach
    void setUp() {
        tallerService = mock(TallerService.class);
        tallerRepository = mock(TallerRepository.class);
        cambios = mock(CambioTallerRepository.class);
        servicio = new DatosTallerService(tallerService, tallerRepository, cambios);

        a = Taller.builder().id("A").nombre("Taller A").ciudad("Bogotá").estadoAprobacion(EstadoAprobacion.APROBADO)
                .tarifaHora(40_000.0).horario("L-V 8-18").build();
        when(tallerService.obtenerPorUsuario("a@demo.com")).thenReturn(a);
        when(tallerService.miTaller("a@demo.com")).thenReturn(TallerDTO.builder().id("A").build());
    }

    private static ActualizarDatosTallerRequest pide(Double tarifa, String horario) {
        ActualizarDatosTallerRequest r = new ActualizarDatosTallerRequest();
        r.setTarifaHora(tarifa);
        r.setHorario(horario);
        return r;
    }

    private static int estado(ResponseStatusException e) {
        return e.getStatusCode().value();
    }

    @Test
    void guardaLaTarifaYElHorarioYRegistraQuienCuandoYElValorAnterior() {
        servicio.actualizar("a@demo.com", pide(55_000.0, "L-S 7-19"));

        ArgumentCaptor<Taller> guardado = ArgumentCaptor.forClass(Taller.class);
        verify(tallerRepository).save(guardado.capture());
        assertEquals(55_000.0, guardado.getValue().getTarifaHora());
        assertEquals("L-S 7-19", guardado.getValue().getHorario());

        ArgumentCaptor<CambioTaller> registro = ArgumentCaptor.forClass(CambioTaller.class);
        verify(cambios, times(2)).save(registro.capture());
        CambioTaller tarifa = registro.getAllValues().stream().filter(c -> c.getCampo().equals("tarifaHora")).findFirst().orElseThrow();
        assertEquals("A", tarifa.getTallerId());
        assertEquals("40000.0", tarifa.getValorAnterior());
        assertEquals("55000.0", tarifa.getValorNuevo());
        assertEquals("a@demo.com", tarifa.getCambiadoPor());
        assertEquals(false, tarifa.getFecha() == null);
    }

    @Test
    void nadaCambiaNiSeRegistraSiElValorEsElMismo() {
        servicio.actualizar("a@demo.com", pide(40_000.0, "L-V 8-18"));
        verify(cambios, never()).save(any());
    }

    @Test
    void soloEditaElTallerDeLaSesion() {
        Taller b = Taller.builder().id("B").estadoAprobacion(EstadoAprobacion.APROBADO).tarifaHora(10_000.0).horario("x").build();
        when(tallerService.obtenerPorUsuario("b@demo.com")).thenReturn(b);
        when(tallerService.miTaller("b@demo.com")).thenReturn(TallerDTO.builder().id("B").build());

        servicio.actualizar("b@demo.com", pide(99_000.0, null));

        assertEquals(99_000.0, b.getTarifaHora());
        assertEquals(40_000.0, a.getTarifaHora());
        verify(tallerRepository, never()).save(a);
    }

    @Test
    void unTallerSinAprobarNoEdita() {
        Taller p = Taller.builder().id("P").estadoAprobacion(EstadoAprobacion.PENDIENTE).build();
        when(tallerService.obtenerPorUsuario("p@demo.com")).thenReturn(p);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.actualizar("p@demo.com", pide(50_000.0, null)));
        assertEquals(403, estado(e));
        verify(tallerRepository, never()).save(any());
    }

    @Test
    void unaTarifaInvalidaSeRechaza() {
        for (Double mala : new Double[]{0.0, -5.0, 1_000_001.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> servicio.actualizar("a@demo.com", pide(mala, null)));
            assertEquals(400, estado(e));
        }
        verify(tallerRepository, never()).save(any());
        verify(cambios, never()).save(any());
    }

    @Test
    void sinNingunValorEsUnaPeticionInvalida() {
        assertEquals(400, estado(assertThrows(ResponseStatusException.class, () -> servicio.actualizar("a@demo.com", pide(null, null)))));
        assertEquals(400, estado(assertThrows(ResponseStatusException.class, () -> servicio.actualizar("a@demo.com", null))));
    }

    @Test
    void elHorarioSeLimpiaYSeLimitaA200() {
        servicio.actualizar("a@demo.com", pide(null, "  <script>alert(1)</script>L-V\n8:00 -\t18:00 <b>sábado</b> "));
        assertEquals("alert(1) L-V 8:00 - 18:00 sábado", a.getHorario());

        servicio.actualizar("a@demo.com", pide(null, "x".repeat(500)));
        assertEquals(200, a.getHorario().length());
    }

    @Test
    void unHorarioVacioLoBorra() {
        servicio.actualizar("a@demo.com", pide(null, "   "));
        assertNull(a.getHorario());
    }

    @Test
    void elRequestNoTieneCamposQueElTallerNoDeberiaCambiar() {
        var campos = java.util.Arrays.stream(ActualizarDatosTallerRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).sorted().toList();
        assertEquals(java.util.List.of("horario", "tarifaHora"), campos);
    }
}
