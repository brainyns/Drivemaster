package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Usuario;

import java.time.Instant;

/**
 * Una cuenta en la gestión de usuarios del SUPERADMIN (/api/usuarios). Sin contraseña, googleId, permisos,
 * intentos fallidos ni datos del bloqueo: esa pantalla solo muestra quién es, su rol y si está activa.
 */
public record UsuarioAdminDTO(
        String id,
        String nombre,
        String correo,
        String rol,
        Boolean activo,
        String proveedor,
        Instant fechaCreacion,
        Instant ultimoLogin) {

    public static UsuarioAdminDTO de(Usuario u) {
        return new UsuarioAdminDTO(u.getId(), u.getNombre(), u.getCorreo(), u.getRol(), u.getActivo(),
                u.getProveedor(), u.getFechaCreacion(), u.getUltimoLogin());
    }
}
