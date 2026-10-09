// Órdenes de taller del flujo anterior (/api/ordenes-servicio/mis-ordenes, solo flujo V1).
// El taller todavía puede avanzarlas desde "Órdenes anteriores", así que alguna puede seguir
// en curso. Sin imports ni JSX: lo prueban los tests con node --test.

/** Estados de una orden del flujo anterior, con el formato de estadosServicio.js (label, tono). */
export const ESTADOS_ORDEN_ANTERIOR = {
  SOLICITADA: { label: "Solicitada", tono: "aviso" },
  COTIZADA: { label: "Cotizada", tono: "aviso" },
  PAGADA: { label: "Pagada", tono: "ok" },
  EN_PROCESO: { label: "En el taller", tono: "proceso" },
  FINALIZADA: { label: "Finalizada", tono: "ok" },
  CANCELADA: { label: "Cancelada", tono: "error" },
};

export const ORDENES_ANTERIORES_ACTIVAS = ["SOLICITADA", "COTIZADA", "PAGADA", "EN_PROCESO"];

/** Las que siguen en curso primero; dentro de cada grupo, de la más reciente a la más antigua. */
export function ordenarOrdenesAnteriores(ordenes = []) {
  const activa = (o) => (ORDENES_ANTERIORES_ACTIVAS.includes(o.estado) ? 0 : 1);
  const fecha = (o) => new Date(o.fechaCreacion || o.fechaProgramada || 0).getTime();
  return [...ordenes].sort((a, b) => activa(a) - activa(b) || fecha(b) - fecha(a));
}

/** Cita futura agendada por el taller (lo que el Historial mostraba como "Citas agendadas"). */
export function tieneCitaProxima(o, ahora = Date.now()) {
  return !!o.fechaProgramada && new Date(o.fechaProgramada).getTime() >= ahora && o.estado !== "CANCELADA";
}