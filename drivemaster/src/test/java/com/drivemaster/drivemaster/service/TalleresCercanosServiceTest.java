package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.TallerCercanoDTO;
import com.drivemaster.drivemaster.dto.TallerMapaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TalleresCercanosServiceTest {

    // Cartagena: Getsemaní como punto del cliente
    private static final double LAT = 10.4220;
    private static final double LNG = -75.5460;

    private TallerService tallerService;
    private OsrmClient osrm;
    private TalleresCercanosService servicio;

    @BeforeEach
    void setUp() {
        tallerService = mock(TallerService.class);
        osrm = mock(OsrmClient.class);
        servicio = new TalleresCercanosService(tallerService, osrm);
        when(tallerService.listarParaMapa()).thenReturn(List.of(
                taller("lejos", 10.3910, -75.4790),     // Centro-sur, ~8 km
                taller("cerca", 10.4235, -75.5450),     // a ~200 m
                taller("medio", 10.4000, -75.5200),     // ~3.7 km
                taller("muyLejos", 10.9640, -74.7960))); // Barranquilla
    }

    private static TallerMapaDTO taller(String id, double lat, double lng) {
        return new TallerMapaDTO(id, "Taller " + id, "Calle 1", "Cartagena", null, lat, lng);
    }

    @Test
    void ordenaPorHaversineYDevuelveSoloLosPrimeros() {
        when(osrm.desde(anyDouble(), anyDouble(), anyList())).thenReturn(List.of());

        List<TallerCercanoDTO> r = servicio.cercanos(LAT, LNG, 3);

        assertEquals(List.of("cerca", "medio", "lejos"), r.stream().map(TallerCercanoDTO::id).toList());
        assertTrue(r.get(0).distanciaKm() < 0.5);
    }

    @Test
    void sinOsrmQuedaLaDistanciaEnLineaRectaYElTiempoEnNull() {
        when(osrm.desde(anyDouble(), anyDouble(), anyList())).thenReturn(List.of());

        TallerCercanoDTO primero = servicio.cercanos(LAT, LNG, 1).get(0);

        assertNull(primero.duracionMin());
        assertNull(primero.distanciaRutaKm());
    }

    @Test
    void conOsrmAgregaDistanciaYTiempoEnCarroEnElMismoOrden() {
        when(osrm.desde(eq(LAT), eq(LNG), anyList())).thenReturn(List.of(
                new OsrmClient.Tramo(0.45, 1.6), new OsrmClient.Tramo(5.04, 11.4)));

        List<TallerCercanoDTO> r = servicio.cercanos(LAT, LNG, 2);

        assertEquals(0.5, r.get(0).distanciaRutaKm());
        assertEquals(2.0, r.get(0).duracionMin());
        assertEquals(5.0, r.get(1).distanciaRutaKm());
        assertEquals(11.0, r.get(1).duracionMin());
    }

    @Test
    void elLimiteSeAcotaEntreUnoYCinco() {
        when(osrm.desde(anyDouble(), anyDouble(), anyList())).thenReturn(List.of());
        assertEquals(1, servicio.cercanos(LAT, LNG, 0).size());
        assertEquals(4, servicio.cercanos(LAT, LNG, 50).size());
    }

    @Test
    void losTalleresSinUbicacionNoEntranEntreLosCercanos() {
        when(tallerService.listarParaMapa()).thenReturn(List.of(
                new TallerMapaDTO("sinPunto", "Taller sin punto", "Calle 2", "Cartagena", null, null, null),
                taller("cerca", 10.4235, -75.5450)));
        when(osrm.desde(anyDouble(), anyDouble(), anyList())).thenReturn(List.of());

        assertEquals(List.of("cerca"), servicio.cercanos(LAT, LNG, 3).stream().map(TallerCercanoDTO::id).toList());
    }

    @Test
    void rechazaCoordenadasFueraDeRango() {
        assertThrows(ResponseStatusException.class, () -> servicio.cercanos(95, LNG, 3));
    }

    @Test
    void leeLaFilaDelOrigenDeLaTablaDeOsrm() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"code":"Ok","durations":[[0,120.0,null]],"distances":[[0,1500.0,null]]}""");

        List<OsrmClient.Tramo> tramos = OsrmClient.leerTabla(json, 2);

        assertEquals(2, tramos.size());
        assertEquals(1.5, tramos.get(0).distanciaKm());
        assertEquals(2.0, tramos.get(0).duracionMin());
        assertNull(tramos.get(1));
    }

    @Test
    void unaRespuestaDeErrorDeOsrmNoDaTramos() throws Exception {
        var json = new ObjectMapper().readTree("{\"code\":\"InvalidQuery\"}");
        assertTrue(OsrmClient.leerTabla(json, 2).isEmpty());
        assertTrue(OsrmClient.leerTabla(null, 2).isEmpty());
    }
}
