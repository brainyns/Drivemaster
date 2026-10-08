// Cálculos compartidos del panel del taller (sin componentes, para no romper Fast Refresh).

/** Órdenes del flujo anterior que el taller todavía debe atender. */
export const ordenesPorAtender = (ordenes = []) =>
  ordenes.filter((o) => o.estado === "PAGADA" || o.estado === "EN_PROCESO"
    || (o.estado === "COTIZADA" && o.fechaPagoReportado));

export const porResponder = (s) => s.estado === "ASIGNADA_A_TALLER" && !s.aceptadaPorTaller;

/** Esperan repuestos: pagada la cotización, aún sin recibirlos en el taller. */
export const esperaRepuestos = (s) => s.estado === "PAGADA" || s.estado === "REPUESTOS_PEDIDOS";

/** Trabajo ya en el taller: repuestos recibidos hasta la entrega al cliente. */
export const enTaller = (s) => ["REPUESTOS_RECIBIDOS", "EN_INSTALACION", "LISTA"].includes(s.estado);

/** Próxima revisión agendada (la más cercana que aún no pasó). */
export function proximaRevision(solicitudes, ahora = Date.now()) {
  return solicitudes
    .filter((s) => s.fechaRevision && ["ASIGNADA_A_TALLER", "EN_REVISION"].includes(s.estado)
      && new Date(s.fechaRevision).getTime() >= ahora)
    .sort((a, b) => new Date(a.fechaRevision) - new Date(b.fechaRevision))[0] || null;
}

/** Cuánto falta (o cuánto pasó) para una fecha, en texto corto. */
export function tiempoRelativo(fecha, ahora = Date.now()) {
  if (!fecha) return "";
  const min = Math.round((new Date(fecha).getTime() - ahora) / 60000);
  const abs = Math.abs(min);
  const texto = abs < 60 ? `${abs} min` : abs < 1440 ? `${Math.round(abs / 60)} h` : `${Math.round(abs / 1440)} d`;
  return min >= 0 ? `en ${texto}` : `hace ${texto}`;
}
