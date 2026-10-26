// Formato de textos para mostrar, sin cambiar los datos guardados.

const MINUSCULAS = new Set(["de", "del", "la", "las", "los", "el", "y", "e", "en"]);

/** "bogotá d.c." → "Bogotá D.C.", "SAN JOSÉ de la montaña" → "San José de la Montaña". */
export function capitalizar(texto) {
  if (!texto) return "";
  return texto
    .trim()
    .toLocaleLowerCase("es-CO")
    .split(/\s+/)
    .map((palabra, i) => {
      if (i > 0 && MINUSCULAS.has(palabra)) return palabra;
      // Siglas con puntos (d.c.) van completas en mayúscula
      if (/^([a-záéíóúñ]\.)+$/i.test(palabra)) return palabra.toLocaleUpperCase("es-CO");
      return palabra.charAt(0).toLocaleUpperCase("es-CO") + palabra.slice(1);
    })
    .join(" ");
}

/** Une partes no vacías: unir(["Cra 1", "Centro"]) → "Cra 1 · Centro". */
export const unir = (partes, separador = " · ") => partes.filter(Boolean).join(separador);

export const formatoFecha = (f) =>
  (f ? new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

export const formatoCop = (n) =>
  (n == null ? "—" : new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n));

/**
 * Duración en minutos, en texto corto: "45 min", "3 h", "3 h 20 min", "1 d 4 h". Menos de un minuto es "< 1 min"
 * (no "0 min", que parece un error). null o undefined → "—".
 */
export function duracionMinutos(min) {
  if (min == null) return "—";
  const total = Math.round(min);
  if (min > 0 && total < 1) return "< 1 min";
  if (total < 60) return `${total} min`;
  if (total < 1440) {
    const h = Math.floor(total / 60);
    const m = total % 60;
    return m ? `${h} h ${m} min` : `${h} h`;
  }
  const d = Math.floor(total / 1440);
  const h = Math.round((total % 1440) / 60);
  if (h === 24) return `${d + 1} d`;
  return h ? `${d} d ${h} h` : `${d} d`;
}

/**
 * Texto del periodo de un informe: "desde el 1 de agosto de 2026". desde es la fecha ISO (yyyy-mm-dd) del primer día
 * que da el backend; sin ella, "de los últimos N meses".
 */
export function textoPeriodo(desde, meses) {
  if (!desde) return `de los últimos ${meses} meses`;
  const [a, m, d] = desde.split("-").map(Number);
  return `desde el ${new Date(a, m - 1, d).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" })}`;
}
