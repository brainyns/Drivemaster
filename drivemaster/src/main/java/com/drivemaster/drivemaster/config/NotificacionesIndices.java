package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.Notificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Índices de la colección notificaciones (la creación automática de índices está apagada):
 * consulta por usuario y fecha, y borrado automático de los avisos con más de 90 días.
 */
@Component
public class NotificacionesIndices implements ApplicationRunner {

    static final Duration RETENCION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(NotificacionesIndices.class);

    private final MongoTemplate mongoTemplate;

    public NotificacionesIndices(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var indices = mongoTemplate.indexOps(Notificacion.class);
            indices.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC).on("fechaCreacion", Sort.Direction.DESC)
                    .named("usuario_fecha"));
            indices.ensureIndex(new Index().on("fechaCreacion", Sort.Direction.ASC).expire(RETENCION)
                    .named("vencimiento_90_dias"));
        } catch (RuntimeException e) {
            log.warn("No se pudieron crear los índices de notificaciones: {}", e.getMessage());
        }
    }
}
