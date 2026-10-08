package com.drivemaster.drivemaster.dto.taller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Informes del TALLER sobre su propia operación. Cada taller recibe solo lo suyo (el filtro por dueño está en el
 * servicio) y sin datos de clientes: los comentarios de calificación van sin nombre ni contacto.
 */
public final class InformesTallerDTO {

    private InformesTallerDTO() {
    }

    /** Solicitudes del taller creadas en el periodo, por estado. */
    public record Servicios(Map<String, Long> porEstado, long total) {}

    /**
     * Dinero de las líneas de reparto del taller. liquidado ya se pagó; pendiente aún no (espera la entrega o ya se puede
     * liquidar). Las líneas anuladas no cuentan.
     */
    public record Monto(double liquidado, long lineasLiquidadas, double pendiente, long lineasPendientes) {}

    /** Órdenes del flujo anterior: suma de la mano de obra de las finalizadas. Va aparte para no mezclar los dos flujos. */
    public record Anteriores(long ordenes, double manoDeObra) {}

    public record Ingresos(Monto manoDeObra, Monto diagnostico, double anulado, Anteriores anteriores) {}

    /**
     * Horas entre ASIGNADA_A_TALLER y EN_REVISION, tomadas de la línea de tiempo de cada solicitud. Sin muestra no hay
     * promedio: horasPromedio y horasMediana son null.
     */
    public record TiempoRespuesta(long muestra, Double horasPromedio, Double horasMediana) {}

    public record Comentario(int calificacion, String comentario, LocalDateTime fecha) {}

    /** promedio es null mientras no haya calificaciones; distribucion cuenta de 1 a 5 estrellas. */
    public record Calificacion(long total, Double promedio, Map<Integer, Long> distribucion, List<Comentario> recientes) {}

    public record Informes(LocalDateTime generadoEn, int mesesConsultados, Servicios servicios, Ingresos ingresos,
                           TiempoRespuesta tiempoRespuesta, Calificacion calificacion) {}
}
