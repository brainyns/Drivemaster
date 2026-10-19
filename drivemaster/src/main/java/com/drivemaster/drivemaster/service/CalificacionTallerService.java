package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calificación de cada taller: el promedio de las notas (1 a 5) que dejan los clientes al confirmar la recepción, y
 * cuántas son. Se guarda en el taller (rating y calificaciones) en vez de calcularse en cada lectura: el mapa del
 * cliente lista todos los talleres en cada visita y la asignación y la predicción de cotización también la leen.
 * Siempre se recalcula desde las órdenes (no se suma una nota al promedio anterior), así un fallo a mitad de camino
 * se corrige con la siguiente calificación o con el recálculo de arranque. Sin notas: rating null y 0 calificaciones.
 */
@Service
public class CalificacionTallerService {

    /** promedio es null mientras no haya calificaciones. */
    public record Resumen(Double promedio, int total) {
        public static final Resumen SIN_CALIFICACIONES = new Resumen(null, 0);
    }

    private final OrdenServicioRepository ordenRepository;
    private final TallerRepository tallerRepository;
    private final MongoTemplate mongo;

    public CalificacionTallerService(OrdenServicioRepository ordenRepository, TallerRepository tallerRepository,
                                     MongoTemplate mongo) {
        this.ordenRepository = ordenRepository;
        this.tallerRepository = tallerRepository;
        this.mongo = mongo;
    }

    /** Promedio con un decimal (el mismo redondeo de los informes) y cantidad; ignora notas fuera de 1 a 5. */
    public static Resumen resumir(Collection<Integer> notas) {
        List<Integer> validas = notas == null ? List.of()
                : notas.stream().filter(Objects::nonNull).filter(n -> n >= 1 && n <= 5).toList();
        if (validas.isEmpty()) return Resumen.SIN_CALIFICACIONES;
        double promedio = validas.stream().mapToInt(Integer::intValue).average().orElse(0);
        return new Resumen(Math.round(promedio * 10.0) / 10.0, validas.size());
    }

    /** Recalcula la calificación de un taller y la guarda (solo esos dos campos). */
    public Resumen recalcular(String tallerId) {
        if (tallerId == null || tallerId.isBlank()) return Resumen.SIN_CALIFICACIONES;
        Resumen r = resumir(ordenRepository.findByTallerIdAndCalificacionTallerNotNull(tallerId).stream()
                .map(OrdenServicio::getCalificacionTaller).toList());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(tallerId)),
                new Update().set("rating", r.promedio()).set("calificaciones", r.total()), Taller.class);
        return r;
    }

    /** Recalcula todos los talleres; devuelve cuántos cambiaron (los demás ya estaban al día). */
    public int recalcularTodos() {
        Map<String, List<Integer>> notas = new HashMap<>();
        for (OrdenServicio o : ordenRepository.findByCalificacionTallerNotNull()) {
            if (o.getTallerId() != null) notas.computeIfAbsent(o.getTallerId(), k -> new ArrayList<>()).add(o.getCalificacionTaller());
        }
        int cambiados = 0;
        for (Taller t : tallerRepository.findAll()) {
            Resumen r = resumir(notas.get(t.getId()));
            int actual = t.getCalificaciones() == null ? -1 : t.getCalificaciones();
            if (Objects.equals(t.getRating(), r.promedio()) && actual == r.total()) continue;
            mongo.updateFirst(Query.query(Criteria.where("_id").is(t.getId())),
                    new Update().set("rating", r.promedio()).set("calificaciones", r.total()), Taller.class);
            cambiados++;
        }
        return cambiados;
    }
}
