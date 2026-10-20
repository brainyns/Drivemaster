package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tiendas y talleres en una sola lista. La vista completa es solo del SUPERADMIN; el ADMIN solo recibe los negocios
 * pendientes de revisión, con lo mínimo para aprobarlos o rechazarlos (sin plan, calificación ni cifras de actividad).
 */
public final class NegociosPlataformaDTO {

    private NegociosPlataformaDTO() {
    }

    public record PlanActual(String codigo, String nombre, LocalDateTime hasta) {}

    /**
     * productos* son de las tiendas y ordenes* de los talleres. calificacion es el promedio de las calificaciones
     * que dieron los clientes (null si todavía no tiene; las tiendas aún no se califican).
     * datosPorCompletar: qué le falta para operar bien en el marketplace (vacío = completo).
     * logoUrl: el logo o la foto que subió el negocio (null = la pantalla muestra sus iniciales).
     */
    public record NegocioFila(String tipo, String id, String nombre, String ciudad, String estado, boolean activo,
                              LocalDateTime registrado, String motivoRechazo, PlanActual plan,
                              Double calificacion, long calificaciones,
                              Long productos, Long productosActivos, Long ordenes, Long ordenesEntregadas,
                              List<String> datosPorCompletar, String logoUrl) {}

    public record NegociosRespuesta(ConsultasPlataformaDTO.Pagina<NegocioFila> negocios, Map<String, Long> porEstado) {}

    /** Lo que el ADMIN necesita para decidir: quién es y qué datos faltan. */
    public record NegocioPendiente(String tipo, String id, String nombre, String ciudad, String direccion, String nit,
                                   LocalDateTime registrado, List<String> datosPorCompletar) {}
}
