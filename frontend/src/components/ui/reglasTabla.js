// Tablas paginadas en el servidor: orden por columna, rango de fechas y la página que devuelve el backend
// (dto.comun.Pagina: items, total, pagina desde 0, tamano, totalPaginas). Reglas puras (se prueban con node --test)
// y un hook. Las vistas son EncabezadoOrden.jsx, FiltroFechas.jsx y Paginacion.jsx.
import { useEffect, useState } from "react";

export const ZONA = "America/Bogota";

// ─── Orden por columna ─────────────────────────────────────────────────────

/**
 * El orden después de un clic en el encabezado: otra columna empieza ascendente; la misma alterna ascendente y
 * descendente. orden es { campo, dir } o null (el orden por defecto del backend).
 */
export function siguienteOrden(orden, campo) {
  if (!orden || orden.campo !== campo) return { campo, dir: "asc" };
  return { campo, dir: orden.dir === "asc" ? "desc" : "asc" };
}

/** El parámetro sort del backend ("campo,asc"), o "" para el orden por defecto. */
export const textoOrden = (orden) => (orden ? `${orden.campo},${orden.dir}` : "");

/** aria-sort del encabezado: solo la columna ordenada lo lleva. */
export function ariaOrden(orden, campo) {
  if (!orden || orden.campo !== campo) return "none";
  return orden.dir === "asc" ? "ascending" : "descending";
}

/** La flecha del encabezado: ↑ ascendente, ↓ descendente y ↕ (tenue) si se puede ordenar pero no está ordenada. */
export function flechaOrden(orden, campo) {
  if (!orden || orden.campo !== campo) return "↕";
  return orden.dir === "asc" ? "↑" : "↓";
}

// ─── Rango de fechas (hora de Colombia) ────────────────────────────────────

/** El día de hoy en Colombia como AAAA-MM-DD (en-CA da ese formato), sin importar la zona del navegador. */
export function hoyColombia(ahora = new Date()) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: ZONA, year: "numeric", month: "2-digit", day: "2-digit" }).format(ahora);
}

/** Suma días a un AAAA-MM-DD (en UTC, para que el cambio de hora del navegador no corra el día). */
export function sumarDias(dia, n) {
  const [a, m, d] = dia.split("-").map(Number);
  const f = new Date(Date.UTC(a, m - 1, d + n));
  return f.toISOString().slice(0, 10);
}

export const RANGOS_RAPIDOS = [
  { clave: "hoy", label: "Hoy" },
  { clave: "7d", label: "Últimos 7 días" },
  { clave: "mes", label: "Este mes" },
];

/** desde y hasta de un acceso rápido; los dos incluidos (el backend toma "hasta" completo). */
export function rangoRapido(clave, ahora = new Date()) {
  const hoy = hoyColombia(ahora);
  if (clave === "hoy") return { desde: hoy, hasta: hoy };
  if (clave === "7d") return { desde: sumarDias(hoy, -6), hasta: hoy };
  if (clave === "mes") return { desde: `${hoy.slice(0, 8)}01`, hasta: hoy };
  return { desde: "", hasta: "" };
}

/** Qué acceso rápido corresponde a un rango: "" sin rango, "personalizado" si no es ninguno. */
export function claveDeRango({ desde = "", hasta = "" } = {}, ahora = new Date()) {
  if (!desde && !hasta) return "";
  const r = RANGOS_RAPIDOS.find((x) => {
    const v = rangoRapido(x.clave, ahora);
    return v.desde === desde && v.hasta === hasta;
  });
  return r ? r.clave : "personalizado";
}

/** El error del rango, como lo diría el backend, para avisarlo antes de pedirlo. */
export function errorRango({ desde = "", hasta = "" } = {}) {
  return desde && hasta && desde > hasta ? "La fecha «desde» no puede ser posterior a «hasta»." : null;
}

/** El rango en palabras para el texto de la tabla: "del 1 al 7 de oct", "desde el 1 oct"… */
export function textoRango({ desde = "", hasta = "" } = {}) {
  const f = (d) => new Date(`${d}T12:00:00`).toLocaleDateString("es-CO", { day: "numeric", month: "short" }).replace(".", "");
  if (desde && hasta) return desde === hasta ? `el ${f(desde)}` : `del ${f(desde)} al ${f(hasta)}`;
  if (desde) return `desde el ${f(desde)}`;
  if (hasta) return `hasta el ${f(hasta)}`;
  return "";
}

// ─── Página del servidor ───────────────────────────────────────────────────

/** Las props de Paginacion (que cuenta desde 1) a partir de la Pagina del backend (que cuenta desde 0). */
export function propsPaginacion(p) {
  return { pagina: (p?.pagina ?? 0) + 1, total: p?.total ?? 0, porPagina: p?.tamano || 20 };
}

/** ?a=1&b=2 solo con los valores que tienen algo (los vacíos no se mandan). */
export function consultaTabla(filtros = {}) {
  const p = new URLSearchParams();
  Object.entries(filtros).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== "") p.set(k, String(v).trim());
  });
  const s = p.toString();
  return s ? `?${s}` : "";
}

/** El valor después de que se deja de escribir (para la búsqueda que va al servidor). */
export function useDiferido(valor, ms = 300) {
  const [diferido, setDiferido] = useState(valor);
  useEffect(() => {
    const id = setTimeout(() => setDiferido(valor), ms);
    return () => clearTimeout(id);
  }, [valor, ms]);
  return diferido;
}
