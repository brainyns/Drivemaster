package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.PlanSuscripcion;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
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
 * 1. Crea la Tienda DriveMaster (la tienda propia de la plataforma).
 * 2. Asigna esa tienda a los productos y movimientos que no tienen tienda.
 * 3. Deja en 0 el stock nulo (el descuento atómico necesita un número).
 * 4. Marca como APROBADO los talleres anteriores al marketplace y les da plan PRUEBA.
 *
 * Corre después de MysqlDataInitializer (planes) y ServicioTallerSeeder (datos demo).
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class MarketplaceMigration implements CommandLineRunner {

    private static final String NOMBRE_TIENDA_PLATAFORMA = "DriveMaster";
    private static final String ACTOR_MIGRACION = "sistema";

    private final MongoTemplate mongoTemplate;
    private final TiendaRepository tiendaRepository;
    private final TallerRepository tallerRepository;
    private final SuscripcionService suscripcionService;

    @Override
    public void run(String... args) {
        Tienda plataforma = asegurarTiendaPlataforma();

        long productos = asignarTienda(Producto.class, plataforma.getId());
        long movimientos = asignarTienda(MovimientoInventario.class, plataforma.getId());
        long stockNulo = mongoTemplate.updateMulti(
                Query.query(Criteria.where("stockActual").is(null)),
                new Update().set("stockActual", 0), Producto.class).getModifiedCount();
        int talleres = aprobarTalleresExistentes();

        log.info("✅ Migración marketplace: tienda plataforma {}, {} productos y {} movimientos asignados, "
                        + "{} productos con stock nulo, {} talleres aprobados",
                plataforma.getId(), productos, movimientos, stockNulo, talleres);
    }

    private Tienda asegurarTiendaPlataforma() {
        return tiendaRepository.findFirstByPlataformaTrue().orElseGet(() -> {
            Tienda tienda = Tienda.builder()
                    .nombre(NOMBRE_TIENDA_PLATAFORMA)
                    .plataforma(true)
                    .estadoAprobacion(EstadoAprobacion.APROBADO)
                    .activo(true)
                    .fechaRegistro(LocalDateTime.now())
                    .fechaAprobacion(LocalDateTime.now())
                    .build();
            Tienda creada = tiendaRepository.save(tienda);
            log.info("➕ Tienda de la plataforma creada: {}. Complete su ciudad y dirección desde administración.",
                    creada.getId());
            return creada;
        });
    }

    private long asignarTienda(Class<?> entidad, String tiendaId) {
        return mongoTemplate.updateMulti(
                Query.query(Criteria.where("tiendaId").is(null)),
                new Update().set("tiendaId", tiendaId),
                entidad).getModifiedCount();
    }

    private int aprobarTalleresExistentes() {
        int migrados = 0;
        for (Taller taller : tallerRepository.findAll()) {
            if (taller.getEstadoAprobacion() != null) continue;
            taller.setEstadoAprobacion(EstadoAprobacion.APROBADO);
            if (taller.getFechaAprobacion() == null) taller.setFechaAprobacion(LocalDateTime.now());
            tallerRepository.save(taller);
            if (suscripcionService.vigente(SuscripcionService.ACTOR_TALLER, taller.getId()).isEmpty()) {
                suscripcionService.activar(SuscripcionService.ACTOR_TALLER, taller.getId(),
                        PlanSuscripcion.PRUEBA, ACTOR_MIGRACION);
            }
            migrados++;
        }
        return migrados;
    }
}
