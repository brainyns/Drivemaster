package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.mysql.PlanSuscripcion;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.service.SuscripcionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Lleva los datos existentes al modelo de marketplace. Es idempotente: cada paso
 * solo toca lo que todavía no está migrado, así que corre en cada arranque sin efecto
 * una vez aplicado.
 *
 * 1. Deja en 0 el stock nulo (el descuento atómico necesita un número).
 * 2. Marca como APROBADO los talleres anteriores al marketplace que tienen una cuenta TALLER válida y les da
 *    plan PRUEBA. Los que no la tienen quedan PENDIENTE e inactivos: sin dueño que pueda entrar no deben
 *    recibir solicitudes, y un taller sin estado contaría como aprobado (EstadoAprobacion.estaAprobado).
 *
 * DriveMaster es la aplicación, no una tienda: aquí ya no se crea ninguna tienda propia ni se asignan
 * productos a una. Los productos sin tienda se quedan sin tienda y no aparecen en ningún catálogo.
 *
 * Corre después de MysqlDataInitializer (planes) y ServicioTallerSeeder (datos demo).
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class MarketplaceMigration implements CommandLineRunner {

    private static final String ACTOR_MIGRACION = "sistema";

    private final MongoTemplate mongoTemplate;
    private final TallerRepository tallerRepository;
    private final SuscripcionService suscripcionService;
    private final UsuarioRepository usuarioRepository;

    @Override
    public void run(String... args) {
        long stockNulo = mongoTemplate.updateMulti(
                Query.query(Criteria.where("stockActual").is(null)),
                new Update().set("stockActual", 0), Producto.class).getModifiedCount();
        int[] talleres = migrarTalleresExistentes();

        log.info("✅ Migración marketplace: {} productos con stock nulo, {} talleres aprobados, {} talleres sin cuenta dejados PENDIENTE",
                stockNulo, talleres[0], talleres[1]);
    }

    /** Devuelve {aprobados, sin cuenta}. */
    private int[] migrarTalleresExistentes() {
        int migrados = 0;
        int sinCuenta = 0;
        for (Taller taller : tallerRepository.findAll()) {
            if (taller.getEstadoAprobacion() != null) continue;
            if (!tieneCuentaTaller(taller)) {
                taller.setEstadoAprobacion(EstadoAprobacion.PENDIENTE);
                taller.setActivo(false);
                tallerRepository.save(taller);
                log.warn("Migración marketplace: el taller {} ({}) no tiene una cuenta TALLER válida; queda PENDIENTE e inactivo",
                        taller.getNombre(), taller.getId());
                sinCuenta++;
                continue;
            }
            taller.setEstadoAprobacion(EstadoAprobacion.APROBADO);
            if (taller.getFechaAprobacion() == null) taller.setFechaAprobacion(LocalDateTime.now());
            tallerRepository.save(taller);
            if (suscripcionService.vigente(SuscripcionService.ACTOR_TALLER, taller.getId()).isEmpty()) {
                suscripcionService.activar(SuscripcionService.ACTOR_TALLER, taller.getId(),
                        PlanSuscripcion.PRUEBA, ACTOR_MIGRACION);
            }
            migrados++;
        }
        return new int[]{migrados, sinCuenta};
    }

    private boolean tieneCuentaTaller(Taller taller) {
        String usuarioId = taller.getUsuarioId();
        if (usuarioId == null || usuarioId.isBlank()) return false;
        return usuarioRepository.findById(usuarioId)
                .map(u -> RolPermisos.TALLER.equals(u.getRol()))
                .orElse(false);
    }
}
