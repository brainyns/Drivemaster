package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Usuario;

/**
 * Lo que ve el usuario de su propia cuenta en Mi perfil (GET y PUT /api/clientes/mi-perfil). Solo datos de
 * contacto: nada de contraseña, googleId, permisos, intentos fallidos, bloqueo ni fechas internas.
 */
public record PerfilClienteResponse(
        String id,
        String nombre,
        String email,
        String telefono,
        String documento,
        String ciudad,
        String direccion,
        String referencia,
        String rol,
        boolean cuentaGoogle) {

    public static PerfilClienteResponse de(Usuario u) {
        return new PerfilClienteResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(),
                u.getIdentificacion(), u.getCiudad(), u.getDireccion(), u.getReferencia(), u.getRol(),
                "GOOGLE".equalsIgnoreCase(u.getProveedor()) || u.getGoogleId() != null);
    }
}
