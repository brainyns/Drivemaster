package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.service.CalificacionTallerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Deja la calificación de cada taller igual al promedio real de sus calificaciones. Los talleres nacían con un 5.0
 * por defecto que nunca se actualizaba: este paso lo reemplaza por el promedio (o por "sin calificaciones": rating
 * null y 0 calificaciones). Es idempotente: solo escribe los talleres que no están al día, así que corre en cada
 * arranque sin efecto una vez aplicado. Corre después de MarketplaceMigration y de los datos demo.
 */
@Slf4j
// Solo en el servidor que ejecuta las tareas de fondo: ver TAREAS_PROGRAMADAS_ENABLED en application.properties.
@ConditionalOnProperty(name = "drivemaster.tareas-programadas.enabled", havingValue = "true", matchIfMissing = true)
@Component
@Order(30)
@RequiredArgsConstructor
public class RecalculoCalificacionesTalleres implements CommandLineRunner {

    private final CalificacionTallerService calificaciones;

    @Override
    public void run(String... args) {
        int cambiados = calificaciones.recalcularTodos();
        if (cambiados > 0) log.info("✅ Calificación de talleres: {} talleres recalculados desde las calificaciones reales", cambiados);
    }
}
