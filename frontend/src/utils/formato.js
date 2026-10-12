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
