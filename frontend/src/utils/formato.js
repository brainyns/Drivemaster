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

/**
 * Cantidad con su sustantivo en singular o plural: plural(1, "unidad", "unidades") → "1 unidad",
 * plural(1200, "unidad", "unidades") → "1.200 unidades". Sin `muchos`, agrega una "s" al singular.
 */
export function plural(n, uno, muchos = `${uno}s`) {
  const valor = Number(n) || 0;
  return `${valor.toLocaleString("es-CO")} ${Math.abs(valor) === 1 ? uno : muchos}`;
}

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

/**
 * Un dato sensible a la vista (cuenta bancaria, documento): solo los últimos 4 caracteres, el resto con puntos.
 * Con 4 o menos caracteres se ocultan todos. Los espacios y guiones no cuentan.
 */
export function enmascarar(valor, visibles = 4) {
  const limpio = String(valor ?? "").replace(/[\s-]/g, "");
  if (!limpio) return "";
  if (limpio.length <= visibles) return "•".repeat(limpio.length);
  return `•••• ${limpio.slice(-visibles)}`;
}

// ── Números con formato de Colombia (es-CO): coma decimal y punto de miles ─────────────────────────────────────────
// 41,3 h · 83,9 % · 0,7 km · 1.234,5. Nunca "41.3" ni "83.9%": así lo lee cualquier persona en Colombia.

/** Un número con hasta `max` decimales (o exactamente `fijo` si se pide, como una calificación 4,0). */
export function decimal(n, { max = 1, fijo = null } = {}) {
  const v = Number(n);
  if (n == null || !Number.isFinite(v)) return "—";
  const d = fijo ?? undefined;
  return v.toLocaleString("es-CO", { minimumFractionDigits: d ?? 0, maximumFractionDigits: d ?? max });
}

/** "83,9 %" con espacio, como se escribe en español. null → "—". */
export const porcentaje = (n, max = 1) => (n == null ? "—" : `${decimal(n, { max })} %`);

/** "41,3 h". */
export const horas = (n) => (n == null ? "—" : `${decimal(n)} h`);

/** "0,7 km". */
export const kilometros = (n) => (n == null ? "—" : `${decimal(n)} km`);

// ── Montos en pesos escritos por la persona ─────────────────────────────────────────────────────────────────────────
// En Colombia "481.500" son cuatrocientos ochenta y un mil quinientos, no 481,5: el punto separa miles y la coma, los
// decimales. Number("481.500") da 481.5; por eso todo monto que se escribe pasa por parsePesos.

/**
 * El número de un monto escrito: quita "$", espacios y los puntos de miles, y acepta coma decimal.
 * parsePesos("481.500") → 481500 · parsePesos("$ 1.234,5") → 1234.5 · un número llega igual. Vacío → null; texto que
 * no es un monto → NaN.
 */
export function parsePesos(texto) {
  if (typeof texto === "number") return Number.isFinite(texto) ? texto : NaN;
  if (texto == null) return null;
  const limpio = String(texto).replace(/[$\s]/g, "");
  if (!limpio) return null;
  if (!/^-?[\d.]*(,\d*)?$/.test(limpio) || !/\d/.test(limpio)) return NaN;
  return Number(limpio.replace(/\./g, "").replace(",", "."));
}

/** Un monto para mostrar, con punto de miles: formatPesos(481500) → "481.500". Sin número → "". */
export function formatPesos(n, maxDecimales = 2) {
  const v = typeof n === "number" ? n : parsePesos(n);
  if (v == null || !Number.isFinite(v)) return "";
  return v.toLocaleString("es-CO", { maximumFractionDigits: maxDecimales });
}

/**
 * Lo que se ve en un campo de dinero mientras se escribe: solo dígitos y una coma decimal (si se permiten), con los
 * puntos de miles puestos. "481500" → "481.500" · "1234,5" → "1.234,5" · "12," se queda así para seguir escribiendo.
 */
export function pesosAlEscribir(texto, { decimales = false } = {}) {
  let t = String(texto ?? "").replace(/[^\d,]/g, "");
  let decimal = null;
  const coma = t.indexOf(",");
  if (coma >= 0) {
    decimal = decimales ? t.slice(coma + 1).replace(/,/g, "").slice(0, 2) : null;
    t = t.slice(0, coma);
  }
  const entero = t.replace(/^0+(?=\d)/, "");
  const conMiles = entero.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  if (decimal == null) return conMiles;
  return `${conMiles || "0"},${decimal}`;
}
