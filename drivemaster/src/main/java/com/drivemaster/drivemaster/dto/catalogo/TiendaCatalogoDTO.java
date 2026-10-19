package com.drivemaster.drivemaster.dto.catalogo;

/**
 * Lo que un cliente ve de una tienda. Solo datos públicos: nombre, ciudad, dirección,
 * horario, si envía y su logo. NIT, teléfono, correo y todo lo administrativo siguen privados.
 */
public record TiendaCatalogoDTO(String id, String nombre, String ciudad, String barrio, String direccion,
                                String horario, boolean ofreceEnvio, Double costoEnvio, String logoUrl) {
}
