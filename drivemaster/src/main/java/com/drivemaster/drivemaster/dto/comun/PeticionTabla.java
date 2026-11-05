package com.drivemaster.drivemaster.dto.comun;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lo que pide una tabla paginada: page (desde 0), size (20 por defecto, máximo 100) y sort ("campo,asc" o
 * "campo,desc"). El campo de sort se busca en una lista blanca (OrdenPermitido) que lo traduce al campo guardado: un
 * campo que no está en la lista responde 400, para que no se pueda ordenar por cualquier cosa.
 */
public record PeticionTabla(int pagina, int tamano, String campoOrden, boolean ascendente, Sort orden) {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    /**
     * Campos que se pueden ordenar en una tabla: nombre del parámetro → campo guardado. porDefecto es el orden sin
     * sort (no tiene que estar en la lista: puede ser una combinación, p. ej. la urgencia de los pedidos).
     */
    public record OrdenPermitido(Map<String, String> campos, Sort porDefecto, String desempate) {
        /** En Mongo: desempate por _id. */
        public static OrdenPermitido de(Sort porDefecto, String... parejas) {
            if (parejas.length % 2 != 0) throw new IllegalArgumentException("Parejas campo → campo guardado");
            Map<String, String> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i < parejas.length; i += 2) m.put(parejas[i], parejas[i + 1]);
            return new OrdenPermitido(Map.copyOf(m), porDefecto, "_id");
        }

        /** El campo que desempata los valores repetidos (en JPA es "id"). */
        public OrdenPermitido conDesempate(String campo) {
            return new OrdenPermitido(campos, porDefecto, campo);
        }
    }

    /**
     * @param page y size: los nombres nuevos; pagina y tamano: los alias de antes (se quitan después de la feria, ver
     *             README). Si llegan los dos, gana el nuevo.
     */
    public static PeticionTabla de(Integer page, Integer size, Integer pagina, Integer tamano, String sort, OrdenPermitido permitido) {
        int p = page != null ? page : pagina != null ? pagina : 0;
        int t = size != null ? size : tamano != null ? tamano : TAMANO_POR_DEFECTO;
        p = Math.max(0, p);
        t = t <= 0 ? TAMANO_POR_DEFECTO : Math.min(t, TAMANO_MAXIMO);

        if (sort == null || sort.isBlank()) return new PeticionTabla(p, t, null, false, permitido.porDefecto());
        String[] partes = sort.trim().split(",");
        String campo = partes[0].trim();
        String guardado = permitido.campos().get(campo);
        if (guardado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede ordenar por «" + campo + "». Se puede por: "
                    + String.join(", ", permitido.campos().keySet().stream().sorted().toList()) + ".");
        }
        String dir = partes.length > 1 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!List.of("asc", "desc").contains(dir) || partes.length > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El orden es «campo,asc» o «campo,desc».");
        }
        boolean asc = "asc".equals(dir);
        // Desempate estable: con valores repetidos, una página no repite ni salta registros
        Sort orden = Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, guardado)
                .and(Sort.by(Sort.Direction.DESC, permitido.desempate()));
        return new PeticionTabla(p, t, campo, asc, orden);
    }

    /** La página que existe para ese total: una page más allá de la última devuelve la última. */
    public int paginaPara(long total) {
        return Math.min(pagina, Pagina.totalPaginas(total, tamano) - 1);
    }

    public PageRequest pageRequest(long total) {
        return PageRequest.of(paginaPara(total), tamano, orden);
    }

    public <T> Pagina<T> pagina(List<T> items, long total) {
        return new Pagina<>(items, total, paginaPara(total), tamano);
    }

    /** Pagina en memoria una lista ya filtrada y ordenada (para las que se arman combinando dos colecciones). */
    public <T> Pagina<T> paginar(List<T> todos) {
        int p = paginaPara(todos.size());
        int desde = Math.min(todos.size(), p * tamano);
        return new Pagina<>(List.copyOf(todos.subList(desde, Math.min(todos.size(), desde + tamano))), todos.size(), p, tamano);
    }
}
