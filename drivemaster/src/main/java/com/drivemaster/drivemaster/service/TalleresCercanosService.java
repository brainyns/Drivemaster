package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.TallerCercanoDTO;
import com.drivemaster.drivemaster.dto.TallerMapaDTO;
import com.drivemaster.drivemaster.util.Coordenadas;
import com.drivemaster.drivemaster.util.HaversineUtil;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Talleres más cercanos a un punto: se ordenan con Haversine sobre las coordenadas guardadas (sin servicios
 * externos) y solo los primeros se refinan con OSRM para mostrar el tiempo real en carro.
 */
@Service
public class TalleresCercanosService {

    public static final int LIMITE_MAX = 5;

    private final TallerService tallerService;
    private final OsrmClient osrm;

    public TalleresCercanosService(TallerService tallerService, OsrmClient osrm) {
        this.tallerService = tallerService;
        this.osrm = osrm;
    }

    public List<TallerCercanoDTO> cercanos(double latitud, double longitud, int limite) {
        Coordenadas.validar(latitud, longitud, false);
        int n = Math.max(1, Math.min(limite, LIMITE_MAX));

        record ConDistancia(TallerMapaDTO taller, double km) {
        }
        List<ConDistancia> primeros = tallerService.listarParaMapa().stream()
                .map(t -> new ConDistancia(t,
                        HaversineUtil.calcularDistanciaKm(latitud, longitud, t.latitud(), t.longitud())))
                .sorted(Comparator.comparingDouble(ConDistancia::km))
                .limit(n)
                .toList();

        List<OsrmClient.Tramo> tramos = osrm.desde(latitud, longitud,
                primeros.stream().map(c -> new double[]{c.taller().latitud(), c.taller().longitud()}).toList());

        return IntStream.range(0, primeros.size()).mapToObj(i -> {
            TallerMapaDTO t = primeros.get(i).taller();
            OsrmClient.Tramo tramo = i < tramos.size() ? tramos.get(i) : null;
            return new TallerCercanoDTO(t.id(), t.nombre(), t.direccion(), t.ciudad(), t.telefono(),
                    t.latitud(), t.longitud(), redondear(primeros.get(i).km()),
                    tramo != null ? redondear(tramo.distanciaKm()) : null,
                    tramo != null ? (double) Math.round(tramo.duracionMin()) : null);
        }).toList();
    }

    private static double redondear(double km) {
        return Math.round(km * 10.0) / 10.0;
    }
}
