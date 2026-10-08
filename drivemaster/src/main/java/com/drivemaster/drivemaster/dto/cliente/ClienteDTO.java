package com.drivemaster.drivemaster.dto.cliente;

import com.drivemaster.drivemaster.model.Usuario;

import java.time.Instant;

/**
 * Lo que el panel ve de un cliente. La lista lleva solo lo necesario para mostrarla; el detalle suma lo que pide el
 * formulario de edición. Nunca salen googleId, rol, permisos, intentos fallidos ni datos de bloqueo.
 */
public final class ClienteDTO {

    private ClienteDTO() {
    }

    public record Resumen(String id, String nombre, String correo, String telefono, String direccion, String ciudad,
                          Instant fechaRegistro, Boolean activo) {
        public static Resumen de(Usuario u) {
            return new Resumen(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(), u.getDireccion(),
                    u.getCiudad(), u.getFechaCreacion(), u.getActivo());
        }
    }

    public record Detalle(String id, String nombre, String correo, String identificacion, String telefono,
                          String direccion, String ciudad, String region, String referencia, Instant fechaRegistro,
                          Boolean activo) {
        public static Detalle de(Usuario u) {
            return new Detalle(u.getId(), u.getNombre(), u.getCorreo(), u.getIdentificacion(), u.getTelefono(),
                    u.getDireccion(), u.getCiudad(), u.getRegion(), u.getReferencia(), u.getFechaCreacion(), u.getActivo());
        }
    }
}
