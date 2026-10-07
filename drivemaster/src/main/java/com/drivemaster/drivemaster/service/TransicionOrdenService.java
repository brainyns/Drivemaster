package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Único punto por donde cambia el estado de una orden del marketplace.
 *
 * Cada cambio:
 *  1. se valida contra la tabla de EstadoOrden (estado origen, destino y actor);
 *  2. se escribe con una actualización condicional en Mongo: solo se aplica si la
 *     orden sigue en el estado leído (y cumple la condición extra que se pida).
 *     Si dos procesos compiten —el job de reasignación y el taller que acepta, o
 *     dos ejecuciones del job— gana uno y el otro recibe 409 sin pisar nada;
 *  3. deja un EventoOrden en la línea de tiempo.
 */
@Service
public class TransicionOrdenService {

    /** Quién ejecuta la acción. */
    public record Actor(String rol, String id, String nombre) {
        public static final Actor SISTEMA = new Actor(EstadoOrden.SISTEMA, null, "DriveMaster");
    }

    private final MongoTemplate mongoTemplate;

    public TransicionOrdenService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Cambia la orden al estado indicado.
     *
     * @param condicion criterios adicionales que deben seguir cumpliéndose (puede ser null)
     * @param cambios   otros campos a modificar en la misma escritura (puede ser null)
     */
    public OrdenServicio transicionar(OrdenServicio orden, String hacia, Actor actor, String nota,
                                      Criteria condicion, Update cambios) {
        return transicionar(orden, hacia, actor, nota, condicion, cambios, List.of());
    }

    /** Igual, dejando además otros eventos en la misma escritura (ej. confirmaciones). */
    public OrdenServicio transicionar(OrdenServicio orden, String hacia, Actor actor, String nota,
                                      Criteria condicion, Update cambios, List<String> notasAdicionales) {
        String desde = orden.getEstado();
        if (!EstadoOrden.permitida(desde, hacia, actor.rol())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede pasar la solicitud de " + desde + " a " + hacia);
        }
        return aplicar(orden, desde, hacia, actor, nota, condicion, cambios, notasAdicionales);
    }

    /** Registra un evento y cambios sin mover el estado (ej. asignar mecánico). */
    public OrdenServicio registrar(OrdenServicio orden, Actor actor, String nota,
                                   Criteria condicion, Update cambios) {
        return registrar(orden, actor, nota, condicion, cambios, List.of());
    }

    public OrdenServicio registrar(OrdenServicio orden, Actor actor, String nota,
                                   Criteria condicion, Update cambios, List<String> notasAdicionales) {
        return aplicar(orden, orden.getEstado(), orden.getEstado(), actor, nota, condicion, cambios, notasAdicionales);
    }

    private OrdenServicio aplicar(OrdenServicio orden, String desde, String hacia, Actor actor, String nota,
                                  Criteria condicion, Update cambios, List<String> notasAdicionales) {
        Criteria base = Criteria.where("id").is(orden.getId()).and("estado").is(desde);
        Criteria criterio = condicion != null ? new Criteria().andOperator(base, condicion) : base;

        LocalDateTime ahora = LocalDateTime.now();
        List<EventoOrden> eventos = new ArrayList<>();
        eventos.add(evento(hacia, ahora, actor, nota));
        if (notasAdicionales != null) {
            notasAdicionales.forEach(n -> eventos.add(evento(hacia, ahora, actor, n)));
        }

        Update update = cambios != null ? cambios : new Update();
        update.set("estado", hacia);
        update.push("eventos").each(eventos.toArray());

        OrdenServicio actualizada = mongoTemplate.findAndModify(Query.query(criterio), update,
                FindAndModifyOptions.options().returnNew(true), OrdenServicio.class);
        if (actualizada == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La solicitud cambió mientras se procesaba. Recarga e intenta de nuevo.");
        }
        return actualizada;
    }

    private static EventoOrden evento(String estado, LocalDateTime fecha, Actor actor, String nota) {
        return EventoOrden.builder()
                .estado(estado)
                .fecha(fecha)
                .actorRol(actor.rol())
                .actorId(actor.id())
                .actorNombre(actor.nombre())
                .nota(nota)
                .build();
    }
}
