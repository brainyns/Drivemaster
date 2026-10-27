package com.drivemaster.drivemaster.dto.comun;

import java.util.List;

/**
 * Formato común de las tablas paginadas en el servidor: los elementos de la página, el total de registros (con los
 * filtros y la búsqueda ya aplicados sobre todos) y el total de páginas. pagina empieza en 0, como el parámetro page.
 */
public record Pagina<T>(List<T> items, long total, int pagina, int tamano, int totalPaginas) {

    public Pagina(List<T> items, long total, int pagina, int tamano) {
        this(items, total, pagina, tamano, totalPaginas(total, tamano));
    }

    /** Al menos 1 (una tabla vacía tiene una página, sin elementos). */
    public static int totalPaginas(long total, int tamano) {
        if (tamano <= 0 || total <= 0) return 1;
        return (int) Math.min(Integer.MAX_VALUE, (total + tamano - 1) / tamano);
    }

    public static <T> Pagina<T> vacia(int tamano) {
        return new Pagina<>(List.of(), 0, 0, tamano);
    }
}
