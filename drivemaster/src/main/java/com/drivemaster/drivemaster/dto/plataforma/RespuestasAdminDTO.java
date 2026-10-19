package com.drivemaster.drivemaster.dto.plataforma;

import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TiendaDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que recibe el ADMIN cuando aprueba o rechaza un negocio o consulta los talleres: sin teléfono, correos,
 * coordenadas, plan ni datos fiscales. El SUPERADMIN sigue recibiendo los DTO completos.
 */
public final class RespuestasAdminDTO {

    private RespuestasAdminDTO() {
    }

    /** Resultado de aprobar o rechazar una tienda o un taller. */
    public record ResultadoAprobacion(String id, String nombre, String ciudad, String estadoAprobacion,
                                      String motivoRechazo, LocalDateTime fechaAprobacion) {

        public static ResultadoAprobacion de(TiendaDTO t) {
            if (t == null) return null;
            return new ResultadoAprobacion(t.getId(), t.getNombre(), t.getCiudad(), t.getEstadoAprobacion(),
                    t.getMotivoRechazo(), t.getFechaAprobacion());
        }

        public static ResultadoAprobacion de(TallerDTO t) {
            if (t == null) return null;
            return new ResultadoAprobacion(t.getId(), t.getNombre(), t.getCiudad(), t.getEstadoAprobacion(),
                    t.getMotivoRechazo(), t.getFechaAprobacion());
        }
    }

    /**
     * Taller en la lista de "Servicios" del ADMIN. rating y calificaciones van juntos, como en TallerDTO: sin la
     * cantidad, la pantalla mostraría "Nuevo" a un taller ya calificado.
     */
    public record TallerResumen(String id, String nombre, String ciudad, String barrio, String direccion,
                                List<String> especialidades, Double rating, Integer calificaciones,
                                Double precioManoObra, String horario, Boolean activo, String estadoAprobacion, String motivoRechazo, LocalDateTime fechaRegistro) {

        public static TallerResumen de(TallerDTO t) {
            if (t == null) return null;
            return new TallerResumen(t.getId(), t.getNombre(), t.getCiudad(), t.getBarrio(), t.getDireccion(),
                    t.getEspecialidades(), t.getRating(),
                    t.getCalificaciones() != null ? t.getCalificaciones() : 0, t.getPrecioManoObra(), t.getHorario(), t.getActivo(),
                    t.getEstadoAprobacion(), t.getMotivoRechazo(), t.getFechaRegistro());
        }
    }
}
