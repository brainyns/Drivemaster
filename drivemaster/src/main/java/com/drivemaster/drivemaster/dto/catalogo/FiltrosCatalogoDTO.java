package com.drivemaster.drivemaster.dto.catalogo;

import java.util.List;

/** Opciones para los filtros: ciudades y tiendas que hoy tienen catálogo, y categorías. */
public record FiltrosCatalogoDTO(List<String> ciudades, List<TiendaCatalogoDTO> tiendas, List<String> categorias) {
}
