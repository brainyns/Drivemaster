package com.drivemaster.drivemaster.dto.catalogo;

/**
 * Lo que un cliente ve de una tienda. Solo datos públicos: nombre, ciudad, dirección,
 * horario y si envía. NIT, teléfono, correo y todo lo administrativo siguen privados.
 */
public record TiendaCatalogoDTO(String id, String nombre, String ciudad, String barrio, String direccion,
                                String horario, boolean ofreceEnvio, Double costoEnvio,
                                // true: es la tienda propia de DriveMaster ("Vendido por DriveMaster")
                                boolean esPlataforma) {
}
