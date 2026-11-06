package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.service.DatosDemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Siembra o borra los datos de demostración de la feria y cierra la aplicación. Solo existe con el perfil demo-seed
 * (arrancando normal no se crea) y solo actúa si DEMO_CONFIRMAR_BASE es exactamente el nombre de la base de Mongo a la
 * que está conectada: así no se corre por error contra otra base. Ver DatosDemoService.
 *
 *   DEMO_ACCION=sembrar | borrar | estado
 */
@Profile("demo-seed")
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DatosDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemoRunner.class);

    private final DatosDemoService demo;
    private final MongoTemplate mongo;
    private final ConfigurableApplicationContext contexto;
    private final String accion;
    private final String confirmarBase;
    private final String postgres;
    /** Solo para el log: en la feria debe ser SIMULADO (las líneas demo que alguien cierre se "pagan" simulado). */
    @Value("${dispersion.modo:SIMULADO}")
    private String modoDispersion = "SIMULADO";

    public DatosDemoRunner(DatosDemoService demo, MongoTemplate mongo, ConfigurableApplicationContext contexto,
                           @Value("${drivemaster.demo.accion:}") String accion,
                           @Value("${drivemaster.demo.confirmar-base:}") String confirmarBase,
                           @Value("${spring.datasource.url:}") String postgres) {
        this.demo = demo;
        this.mongo = mongo;
        this.contexto = contexto;
        this.accion = accion == null ? "" : accion.trim().toLowerCase();
        this.confirmarBase = confirmarBase == null ? "" : confirmarBase.trim();
        this.postgres = postgres;
    }

    @Override
    public void run(String... args) {
        int codigo = ejecutar();
        System.exit(SpringApplication.exit(contexto, () -> codigo));
    }

    /** 0 si terminó bien; 2 si no se confirmó la base o la acción no existe; 1 si falló. */
    int ejecutar() {
        String base = mongo.getDb().getName();
        log.info("Datos demo: acción '{}' sobre la base Mongo '{}' y Postgres {}; dispersión {}", accion, base,
                sinCredenciales(postgres), modoDispersion);
        if (!"SIMULADO".equalsIgnoreCase(modoDispersion)) {
            log.warn("Datos demo: DISPERSION_MODO es {}. Para la feria debe ser SIMULADO.", modoDispersion);
        }
        if (!base.equals(confirmarBase)) {
            log.error("Datos demo: no se hizo nada. DEMO_CONFIRMAR_BASE debe ser exactamente '{}' (llegó '{}')", base, confirmarBase);
            return 2;
        }
        try {
            switch (accion) {
                case "sembrar" -> log.info(demo.sembrar());
                case "borrar" -> log.info(demo.borrar().toString());
                case "estado" -> log.info("Datos demo: {}", demo.descripcionEstado());
                default -> {
                    log.error("Datos demo: DEMO_ACCION debe ser sembrar, borrar o estado (llegó '{}')", accion);
                    return 2;
                }
            }
            return 0;
        } catch (RuntimeException e) {
            log.error("Datos demo: la acción '{}' falló", accion, e);
            return 1;
        }
    }

    /** La URL de Postgres sin usuario ni clave, para el log. */
    static String sinCredenciales(String url) {
        if (url == null) return "";
        return url.replaceAll("//[^@/]*@", "//").replaceAll("(?i)(password=)[^&]*", "$1***");
    }
}
