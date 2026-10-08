package com.drivemaster.drivemaster.dto.cliente;

import lombok.Data;

/**
 * Datos que se aceptan al crear o editar un cliente. No existen rol, permisos ni activo, y al editar el correo y la
 * contraseña se ignoran: lo que llegue con esos nombres en el JSON se descarta o no se usa.
 */
@Data
public class DatosClienteRequest {
    private String nombre;
    private String correo;      // solo al crear
    private String password;    // solo al crear
    private String identificacion;
    private String telefono;
    private String direccion;
    private String ciudad;
    private String region;
    private String referencia;
}
