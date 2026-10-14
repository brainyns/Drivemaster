package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsignacionTallerServiceTest {

    private TallerRepository tallerRepository;
    private ParametrosMarketplace parametros;
    private AsignacionTallerService service;

    // Coordenadas aproximadas en Bogotá y alrededores
    private static final double CHAPINERO_LAT = 4.6486, CHAPINERO_LNG = -74.0631;

    @BeforeEach
    void setUp() {
        tallerRepository = mock(TallerRepository.class);
        parametros = mock(ParametrosMarketplace.class);
        when(parametros.radioMaxKm()).thenReturn(30.0);
        service = new AsignacionTallerService(tallerRepository, null, parametros, null, null);
    }

    private static Taller taller(String id, String ciudad, Double lat, Double lng, String estado, double rating) {
        return Taller.builder().id(id).nombre(id).ciudad(ciudad).latitud(lat).longitud(lng)
                .estadoAprobacion(estado).rating(rating).activo(true).build();
    }

    private static OrdenServicio orden(String ciudad, Double lat, Double lng, String... descartados) {
        return OrdenServicio.builder().ciudad(ciudad).latitud(lat).longitud(lng)
                .talleresDescartados(new ArrayList<>(List.of(descartados))).build();
    }

    @Test
    void prefiereLaMismaCiudadOrdenadaPorDistancia() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("lejos", "Bogotá D.C.", 4.75, -74.09, EstadoAprobacion.APROBADO, 5),
                taller("cerca", "Bogota", 4.65, -74.06, EstadoAprobacion.APROBADO, 3),
                taller("chia", "Chía", 4.86, -74.05, EstadoAprobacion.APROBADO, 5)));

        var candidatos = service.candidatos(orden("bogotá", CHAPINERO_LAT, CHAPINERO_LNG));

        assertEquals(List.of("cerca", "lejos"), candidatos.stream().map(c -> c.taller().getId()).toList());
    }

    @Test
    void excluyeDescartadosPendientesYRechazados() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("descartado", "Bogotá", 4.65, -74.06, EstadoAprobacion.APROBADO, 5),
                taller("pendiente", "Bogotá", 4.65, -74.06, EstadoAprobacion.PENDIENTE, 5),
                taller("rechazado", "Bogotá", 4.65, -74.06, EstadoAprobacion.RECHAZADO, 5),
                taller("antiguo", "Bogotá", 4.70, -74.08, null, 4)));

        var candidatos = service.candidatos(orden("Bogotá", CHAPINERO_LAT, CHAPINERO_LNG, "descartado"));

        // Los talleres anteriores al marketplace (sin estado) cuentan como aprobados.
        assertEquals(List.of("antiguo"), candidatos.stream().map(c -> c.taller().getId()).toList());
    }

    @Test
    void sinTallerEnLaCiudadUsaElMasCercanoDentroDelRadio() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("chia", "Chía", 4.86, -74.05, EstadoAprobacion.APROBADO, 5),        // ~24 km
                taller("medellin", "Medellín", 6.24, -75.58, EstadoAprobacion.APROBADO, 5)));

        var candidatos = service.candidatos(orden("Soacha", CHAPINERO_LAT, CHAPINERO_LNG));

        assertEquals(List.of("chia"), candidatos.stream().map(c -> c.taller().getId()).toList());
    }

    @Test
    void sinCoordenadasNiTallerEnLaCiudadNoHayCandidatos() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("chia", "Chía", 4.86, -74.05, EstadoAprobacion.APROBADO, 5)));

        assertTrue(service.candidatos(orden("Soacha", null, null)).isEmpty());
    }

    @Test
    void sinCoordenadasOrdenaPorRating() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("tres", "Cali", null, null, EstadoAprobacion.APROBADO, 3),
                taller("cinco", "Cali", null, null, EstadoAprobacion.APROBADO, 5)));

        var candidatos = service.candidatos(orden("cali", null, null));

        assertEquals(List.of("cinco", "tres"), candidatos.stream().map(c -> c.taller().getId()).toList());
    }

    @Test
    void elTallerElegidoEnElMapaVaPrimeroAunqueSeaDeOtraCiudad() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("cerca", "Bogotá", 4.65, -74.06, EstadoAprobacion.APROBADO, 5),
                taller("chia", "Chía", 4.86, -74.05, EstadoAprobacion.APROBADO, 3)));
        OrdenServicio orden = orden("Bogotá", CHAPINERO_LAT, CHAPINERO_LNG);
        orden.setTallerElegidoId("chia");

        var candidatos = service.candidatos(orden);

        assertEquals(List.of("chia"), candidatos.stream().map(c -> c.taller().getId()).toList());
    }

    @Test
    void siElElegidoYaLaDescartoSigueLaAsignacionPorCiudad() {
        when(tallerRepository.findByActivoTrue()).thenReturn(List.of(
                taller("cerca", "Bogotá", 4.65, -74.06, EstadoAprobacion.APROBADO, 5),
                taller("chia", "Chía", 4.86, -74.05, EstadoAprobacion.APROBADO, 3)));
        OrdenServicio orden = orden("Bogotá", CHAPINERO_LAT, CHAPINERO_LNG, "chia");
        orden.setTallerElegidoId("chia");

        var candidatos = service.candidatos(orden);

        assertEquals(List.of("cerca"), candidatos.stream().map(c -> c.taller().getId()).toList());
    }

    @Test
    void normalizaNombresDeCiudad() {
        assertEquals("bogota", AsignacionTallerService.normalizarCiudad("Bogotá D.C."));
        assertEquals("bogota", AsignacionTallerService.normalizarCiudad("  BOGOTA "));
        assertEquals("medellin", AsignacionTallerService.normalizarCiudad("Medellín"));
        assertEquals("", AsignacionTallerService.normalizarCiudad(null));
    }
}
