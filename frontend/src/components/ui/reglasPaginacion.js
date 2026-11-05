// Paginación de las listas del panel: reglas puras (se prueban con node --test) y el hook que las usa. La vista es
// Paginacion.jsx. Todas las listas usan el mismo tamaño y el mismo formato: "Mostrando 1–20 de 135", anterior,
// números de página y siguiente.
import { useState } from "react";

export const POR_PAGINA = 20;

export const totalPaginas = (total, porPagina = POR_PAGINA) => Math.max(1, Math.ceil((Number(total) || 0) / porPagina));

/** Los elementos de la página (1 = la primera). */
export const paginar = (lista, pagina, porPagina = POR_PAGINA) => lista.slice((pagina - 1) * porPagina, pagina * porPagina);

/** "Mostrando desde–hasta de total": desde y hasta de la página (0 y 0 si no hay nada). */
export function rangoMostrado(pagina, total, porPagina = POR_PAGINA) {
  if (!total) return { desde: 0, hasta: 0 };
  return { desde: (pagina - 1) * porPagina + 1, hasta: Math.min(pagina * porPagina, total) };
}

/**
 * Los números a mostrar: siempre la primera, la última y dos alrededor de la actual; los huecos son "…".
 * paginasVisibles(6, 12) → [1, "…", 4, 5, 6, 7, 8, "…", 12].
 */
export function paginasVisibles(actual, paginas) {
  const set = new Set([1, paginas]);
  for (let p = actual - 2; p <= actual + 2; p++) if (p >= 1 && p <= paginas) set.add(p);
  const orden = [...set].sort((a, b) => a - b);
  return orden.flatMap((p, i) => (i > 0 && p - orden[i - 1] > 1 ? ["…", p] : [p]));
}

/**
 * Página actual de una lista paginada en el navegador. clave resume los filtros y la búsqueda: si cambia, la lista
 * vuelve a la página 1 (sin efectos: la página guardada solo vale para la clave con la que se eligió). Si la lista se
 * acorta (por ejemplo al eliminar), la página se ajusta a la última que exista.
 */
export function usePaginacion(total, clave = "", porPagina = POR_PAGINA) {
  const [estado, setEstado] = useState({ pagina: 1, clave });
  const elegida = estado.clave === clave ? estado.pagina : 1;
  const pagina = Math.min(elegida, totalPaginas(total, porPagina));
  return { pagina, porPagina, cambiar: (p) => setEstado({ pagina: p, clave }) };
}
