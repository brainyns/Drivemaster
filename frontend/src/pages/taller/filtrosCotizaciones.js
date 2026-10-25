// Pestañas de Cotizaciones del taller (lógica pura: se prueba con node --test).

/** Una cotización emitida cuya vigencia ya pasó cuenta como vencida aunque el sistema aún no la haya cerrado. */
export const vencida = (c, ahora) =>
  c.estado === "VENCIDA" || (c.estado === "EMITIDA" && !!c.vigenciaHasta && new Date(c.vigenciaHasta).getTime() < ahora);

// Con esta vigencia o menos, una cotización emitida se marca "Vence pronto"
const DIAS_VENCE_PRONTO = 2;

/** Emitida, todavía vigente y a 2 días o menos de vencer. */
export const vencePronto = (c, ahora) =>
  c.estado === "EMITIDA" && !!c.vigenciaHasta && !vencida(c, ahora)
  && new Date(c.vigenciaHasta).getTime() - ahora <= DIAS_VENCE_PRONTO * 86400000;

/** En este orden se muestran las pestañas. */
export const FILTROS = [
  { key: "emitidas", label: "Emitidas", filtro: (c, ahora) => c.estado === "EMITIDA" && !vencida(c, ahora) },
  { key: "aprobadas", label: "Aprobadas", filtro: (c) => c.estado === "APROBADA" },
  { key: "rechazadas", label: "Rechazadas", filtro: (c) => c.estado === "RECHAZADA" },
  { key: "vencidas", label: "Vencidas", filtro: vencida },
];

export const VACIO = {
  emitidas: "No tienes cotizaciones esperando respuesta. Las emites desde una solicitud diagnosticada, en Solicitudes.",
  aprobadas: "Aún ninguna cotización aprobada. Cuando el cliente apruebe y pague, el trabajo pasa a Trabajos.",
  rechazadas: "Ningún cliente ha rechazado tus cotizaciones.",
  vencidas: "No tienes cotizaciones vencidas.",
};

/** Cuántas cotizaciones hay en cada pestaña. */
export function contarPorFiltro(cotizaciones, ahora) {
  return Object.fromEntries(FILTROS.map((f) => [f.key, cotizaciones.filter((c) => f.filtro(c, ahora)).length]));
}

export function filtrar(cotizaciones, key, ahora) {
  const f = FILTROS.find((x) => x.key === key) || FILTROS[0];
  return cotizaciones.filter((c) => f.filtro(c, ahora));
}
