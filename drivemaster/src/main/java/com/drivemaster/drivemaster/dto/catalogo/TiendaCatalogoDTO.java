package com.drivemaster.drivemaster.dto.catalogo;

/**
 * Lo que un cliente ve de una tienda. Solo datos públicos: nombre, ciudad, dirección,
 * horario (en texto y, si lo configuró, por días), si envía y su logo. NIT, teléfono, correo y todo lo administrativo siguen privados.
 */
public record TiendaCatalogoDTO(String id, String nombre, String ciudad, String barrio, String direccion,
                                String horario, boolean ofreceEnvio, Double costoEnvio, String logoUrl,
                                java.util.List<com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO> horarioSemanal) {
}
