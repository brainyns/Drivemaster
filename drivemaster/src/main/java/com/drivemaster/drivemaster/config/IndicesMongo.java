package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.CambioCorreo;
import com.drivemaster.drivemaster.model.CambioTaller;
import com.drivemaster.drivemaster.model.HorarioTaller;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.RecuperacionClave;
import com.drivemaster.drivemaster.model.Usuario;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Índices de las colecciones que más se consultan, creados al arrancar (la creación automática de Spring Data está
 * apagada a propósito: si un índice falla, aquí se avisa en el log y la aplicación arranca igual).
 *
 * Cada índice sigue una consulta real:
 * - ordenes_servicio: bandeja y citas del taller, servicios del cliente, historial del vehículo y los vencimientos que
 *   revisa ReasignacionScheduler.
 * - horarios_taller y cambios_taller: todo se busca por tallerId.
 * - cambios_correo y recuperaciones_clave: por usuarioId o correo, y un TTL en "vence" para que los códigos
 *   olvidados no se acumulen.
 * - usuarios.correo único: solo si no hay correos repetidos. Si los hay, no se crea y se listan en el log para
 *   resolverlos a mano (ver MIGRACIONES_PENDIENTES.md).
 */
@Component
public class IndicesMongo implements ApplicationRunner {

    static final String CORREO_UNICO = "correo_unico";

    private static final Logger log = LoggerFactory.getLogger(IndicesMongo.class);

    private final MongoTemplate mongoTemplate;

    public IndicesMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        crear("ordenes_servicio", () -> {
            IndexOperations ordenes = mongoTemplate.indexOps(OrdenServicio.class);
            // Bandeja del taller y órdenes del flujo anterior: tallerId (+ flujo), de la más nueva a la más vieja
            ordenes.ensureIndex(new Index().on("tallerId", Sort.Direction.ASC).on("flujo", Sort.Direction.ASC)
                    .on("fechaCreacion", Sort.Direction.DESC).named("taller_flujo_fecha"));
            // Servicios del cliente (Mis servicios y el flujo anterior)
            ordenes.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC).on("flujo", Sort.Direction.ASC)
                    .on("fechaCreacion", Sort.Direction.DESC).named("usuario_flujo_fecha"));
            // Agenda: citas del taller por rango de fechas
            ordenes.ensureIndex(new Index().on("tallerId", Sort.Direction.ASC).on("fechaProgramada", Sort.Direction.ASC)
                    .named("taller_cita"));
            // Historial y validaciones por vehículo
            ordenes.ensureIndex(new Index().on("vehiculoId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                    .named("vehiculo_estado"));
            // Vencimientos y listados por estado del panel de la plataforma
            ordenes.ensureIndex(new Index().on("flujo", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                    .named("flujo_estado"));
        });
        crear("horarios_taller", () -> mongoTemplate.indexOps(HorarioTaller.class)
                .ensureIndex(new Index().on("tallerId", Sort.Direction.ASC).named("taller")));
        crear("cambios_taller", () -> mongoTemplate.indexOps(CambioTaller.class)
                .ensureIndex(new Index().on("tallerId", Sort.Direction.ASC).named("taller")));
        crear("cambios_correo", () -> {
            IndexOperations pendientes = mongoTemplate.indexOps(CambioCorreo.class);
            pendientes.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC).named("usuario"));
            // TTL: MongoDB borra solo los cambios que vencieron sin confirmarse
            pendientes.ensureIndex(new Index().on("vence", Sort.Direction.ASC).expire(0).named("vence_ttl"));
        });
        crear("recuperaciones_clave", () -> {
            IndexOperations pendientes = mongoTemplate.indexOps(RecuperacionClave.class);
            pendientes.ensureIndex(new Index().on("correo", Sort.Direction.ASC).named("correo"));
            pendientes.ensureIndex(new Index().on("vence", Sort.Direction.ASC).expire(0).named("vence_ttl"));
        });
        correoUnico();
        contarCorreosConMayusculas();
    }

    /** Solo informa: los correos guardados con mayúsculas o espacios se migran a mano (MIGRACIONES_PENDIENTES.md). */
    void contarCorreosConMayusculas() {
        try {
            long n = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(
                    Criteria.where("correo").regex("[A-Z]|^\\s|\\s$")), Usuario.class);
            if (n > 0) {
                log.warn("Hay {} usuarios con el correo guardado con mayúsculas o espacios. El inicio de sesión los encuentra "
                        + "igual; para normalizarlos ver MIGRACIONES_PENDIENTES.md.", n);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron contar los correos con mayúsculas: {}", e.getMessage());
        }
    }

    /** Índice único de usuarios.correo, solo si no hay repetidos exactos. Nunca detiene el arranque. */
    void correoUnico() {
        try {
            List<String> repetidos = correosDuplicados(false);
            if (!repetidos.isEmpty()) {
                log.warn("No se creó el índice único de usuarios.correo: hay {} correos repetidos ({}). "
                        + "Resuélvelos a mano y reinicia (ver MIGRACIONES_PENDIENTES.md).",
                        repetidos.size(), String.join(", ", repetidos.stream().map(IndicesMongo::enmascarar).toList()));
                return;
            }
            List<String> soloMayusculas = correosDuplicados(true);
            if (!soloMayusculas.isEmpty()) {
                log.warn("Hay {} correos que solo se diferencian en mayúsculas ({}); el índice único no los detecta.",
                        soloMayusculas.size(), String.join(", ", soloMayusculas.stream().map(IndicesMongo::enmascarar).toList()));
            }
            // Parcial: las cuentas sin correo (si existieran) no chocan entre sí
            mongoTemplate.indexOps(Usuario.class).ensureIndex(new Index().on("correo", Sort.Direction.ASC).unique()
                    .partial(PartialIndexFilter.of(Criteria.where("correo").type(2))).named(CORREO_UNICO));
        } catch (RuntimeException e) {
            log.warn("No se pudo crear el índice único de usuarios.correo: {}", e.getMessage());
        }
    }

    /** Correos que aparecen en más de un usuario; con ignorarMayusculas, comparando en minúsculas. */
    List<String> correosDuplicados(boolean ignorarMayusculas) {
        Object clave = ignorarMayusculas ? new Document("$toLower", "$correo") : "$correo";
        List<Document> etapas = List.of(
                new Document("$match", new Document("correo", new Document("$type", "string"))),
                new Document("$group", new Document("_id", clave).append("n", new Document("$sum", 1))),
                new Document("$match", new Document("n", new Document("$gt", 1))));
        List<String> correos = new ArrayList<>();
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(Usuario.class)).aggregate(etapas)
                .forEach(d -> correos.add(String.valueOf(d.get("_id"))));
        return correos;
    }

    /** "juan.perez@gmail.com" → "ju***@gmail.com": el log no guarda el correo completo. */
    static String enmascarar(String correo) {
        int arroba = correo == null ? -1 : correo.indexOf('@');
        if (arroba < 0) return "***";
        return correo.substring(0, Math.min(2, arroba)) + "***" + correo.substring(arroba);
    }

    private void crear(String coleccion, Runnable indices) {
        try {
            indices.run();
        } catch (RuntimeException e) {
            log.warn("No se pudieron crear los índices de {}: {}", coleccion, e.getMessage());
        }
    }
}
