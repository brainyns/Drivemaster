// Cálculos del gráfico de barras (Grafico.jsx), aparte para probarlos sin React.

/**
 * Tope "redondo" del eje para un máximo: 1, 2, 2,5 o 5 por una potencia de 10 (87.000 → 100.000, 3 → 5).
 * Sin datos (0 o negativo), 1: las barras quedan en cero sin dividir por cero.
 */
export function topeEje(maximo) {
  if (!(maximo > 0)) return 1;
  const potencia = 10 ** Math.floor(Math.log10(maximo));
  for (const paso of [1, 2, 2.5, 5, 10]) {
    if (maximo <= paso * potencia) return paso * potencia;
  }
  return 10 * potencia;
}

/** Marcas del eje: 0, la mitad y el tope. */
export const marcasEje = (tope) => [0, tope / 2, tope];

/** Alto de una barra en % del tope (0 a 100). */
export const altoBarra = (valor, tope) => (tope > 0 ? Math.max(0, Math.min(100, (valor * 100) / tope)) : 0);

/** "2026-08" → "ago" (con el año si se pide: "ago 2026"). */
export function etiquetaMes(mes, conAnio = false) {
  const [a, m] = mes.split("-").map(Number);
  const fecha = new Date(a, m - 1, 1);
  const corto = fecha.toLocaleDateString("es-CO", { month: "short" }).replace(".", "");
  return conAnio ? `${corto} ${a}` : corto;
}

/** "2026-10-05" → "5 oct": el lunes con que empieza una semana. */
export function etiquetaSemana(inicio) {
  const [a, m, d] = inicio.split("-").map(Number);
  // Sin el "de" que pone es-CO ("5 de oct"): bajo una columna angosta sobra
  return `${d} ${etiquetaMes(`${a}-${m}`)}`;
}

/** Pesos en corto para el eje: $0, $50 mil, $1,2 M. */
export function pesosCorto(n) {
  if (n >= 1_000_000) return `$${(n / 1_000_000).toLocaleString("es-CO", { maximumFractionDigits: 1 })} M`;
  if (n >= 1_000) return `$${(n / 1_000).toLocaleString("es-CO", { maximumFractionDigits: 0 })} mil`;
  return `$${Math.round(n)}`;
}
