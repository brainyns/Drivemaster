package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Notificacion;
import com.drivemaster.drivemaster.repository.NotificacionRepository;
import com.drivemaster.drivemaster.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avisos de la campanita: se guardan en Mongo (para verlos al volver a entrar) y se envían en
 * tiempo real a las pestañas abiertas del destinatario. Avisar nunca lanza excepciones: un fallo
 * aquí no puede romper un pago, una cotización ni un pedido.
 */
@Service
public class NotificacionService {

    static final int LIMITE_POR_DEFECTO = 30;
    static final int LIMITE_MAXIMO = 100;

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository repository;
    private final MongoTemplate mongoTemplate;
    private final NotificationWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public NotificacionService(NotificacionRepository repository, MongoTemplate mongoTemplate,
                               NotificationWebSocketHandler handler, ObjectMapper objectMapper) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    /**
     * Guarda y envía un aviso. Si hay una transacción en curso, espera a que se confirme
     * (un aviso de un pago que se revierte no debe salir).
     */
    public void avisar(String usuarioId, String tipo, String titulo, String mensaje, String enlace, String referenciaId) {
        if (usuarioId == null || usuarioId.isBlank()) return;
        Runnable tarea = () -> guardarYEnviar(usuarioId, tipo, titulo, mensaje, enlace, referenciaId);
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        tarea.run();
                    }
                });
            } else {
                tarea.run();
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo programar el aviso '{}' para el usuario {}: {}", tipo, usuarioId, e.getMessage());
        }
    }

    public List<Notificacion> mias(String usuarioId, Integer limite) {
        int tam = limite == null ? LIMITE_POR_DEFECTO : Math.max(1, Math.min(LIMITE_MAXIMO, limite));
        return repository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId, PageRequest.of(0, tam));
    }

    public long noLeidas(String usuarioId) {
        return repository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    /** Marca un aviso propio como leído. false si no existe o es de otro usuario. */
    public boolean marcarLeida(String usuarioId, String id) {
        Query q = new Query(Criteria.where("_id").is(id).and("usuarioId").is(usuarioId));
        Update u = new Update().set("leida", true).set("fechaLectura", Instant.now());
        return mongoTemplate.updateFirst(q, u, Notificacion.class).getMatchedCount() > 0;
    }

    public long marcarTodasLeidas(String usuarioId) {
        Query q = new Query(Criteria.where("usuarioId").is(usuarioId).and("leida").is(false));
        Update u = new Update().set("leida", true).set("fechaLectura", Instant.now());
        return mongoTemplate.updateMulti(q, u, Notificacion.class).getModifiedCount();
    }

    /** Corta las conexiones abiertas de una cuenta (al desactivarla). */
    public void cerrarConexionesDe(String usuarioId) {
        try {
            handler.cerrarSesionesDe(usuarioId);
        } catch (RuntimeException e) {
            log.warn("No se pudieron cerrar las conexiones del usuario {}: {}", usuarioId, e.getMessage());
        }
    }

    private void guardarYEnviar(String usuarioId, String tipo, String titulo, String mensaje, String enlace, String referenciaId) {
        try {
            Notificacion guardada = repository.save(Notificacion.builder()
                    .usuarioId(usuarioId).tipo(tipo).titulo(titulo).mensaje(mensaje)
                    .enlace(enlace).referenciaId(referenciaId)
                    .leida(false).fechaCreacion(Instant.now())
                    .build());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "notificacion");
            payload.put("notificacion", guardada);
            payload.put("noLeidas", noLeidas(usuarioId));
            handler.enviarAUsuario(usuarioId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("No se pudo guardar o enviar el aviso '{}' para el usuario {}: {}", tipo, usuarioId, e.getMessage());
        }
    }
}
