package com.drivemaster.drivemaster.dto.catalogo;

import java.util.List;

public record PaginaCatalogoDTO(List<ProductoCatalogoDTO> items, long total, int pagina, int tamanio) {
}
