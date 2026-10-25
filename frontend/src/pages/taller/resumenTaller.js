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

// Lo comparten las secciones del taller y la tienda: vive en utils/navegacion.js
export { destinoSeccion } from "../../utils/navegacion.js";

/** Revisión aceptada que aún no se diagnostica (la cita está en pie). */
export const agendada = (s) => (s.estado === "ASIGNADA_A_TALLER" && s.aceptadaPorTaller) || s.estado === "EN_REVISION";

const mismoDia = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

/** Citas de revisión de hoy (hora local), de la más temprana a la más tarde. */
export function citasDeHoy(solicitudes, ahora = Date.now()) {
  const hoy = new Date(ahora);
  return solicitudes
    .filter((s) => agendada(s) && s.fechaRevision && mismoDia(new Date(s.fechaRevision), hoy))
    .sort((a, b) => new Date(a.fechaRevision) - new Date(b.fechaRevision));
}

// Menos de esto para el límite de respuesta: la solicitud se marca como crítica
const HORAS_CRITICAS = 2;

/**
 * Lo que pide acción del taller, lo más urgente primero: solicitudes por responder (por vencimiento), citas de hoy,
 * diagnósticos por cotizar, repuestos por confirmar y vehículos listos para entregar. Cada ítem dice qué hacer
 * (accion), a qué sección ir (pagina + filtro) y con qué tono marcarlo.
 */
export function urgentes(solicitudes, ahora = Date.now()) {
  const items = [];
  solicitudes.filter(porResponder).forEach((s) => {
    const horas = s.fechaLimiteRespuesta ? (new Date(s.fechaLimiteRespuesta).getTime() - ahora) / 3600000 : Infinity;
    items.push({
      s, orden: 0, clave: new Date(s.fechaLimiteRespuesta || 0).getTime(),
      estado: s.fechaLimiteRespuesta ? `Responde ${tiempoRelativo(s.fechaLimiteRespuesta, ahora)}` : "Por responder",
      tono: horas < HORAS_CRITICAS ? "error" : "aviso",
      accion: "Responder", pagina: "taller-solicitudes", filtro: "responder",
    });
  });
  citasDeHoy(solicitudes, ahora).forEach((s) => items.push({
    s, orden: 1, clave: new Date(s.fechaRevision).getTime(),
    estado: s.estado === "EN_REVISION" ? "En revisión hoy" : "Cita hoy", tono: "nueva",
    accion: s.estado === "EN_REVISION" ? "Diagnosticar" : "Ver cita", pagina: "taller-solicitudes", filtro: "agendadas",
  }));
  const resto = [
    { filtro: (s) => s.estado === "DIAGNOSTICADA", estado: "Por cotizar", tono: "cotizacion", accion: "Cotizar", pagina: "taller-solicitudes", f: "diagnosticadas" },
    { filtro: (s) => s.estado === "REPUESTOS_PEDIDOS", estado: "Esperando repuestos", tono: "curso", accion: "Confirmar llegada", pagina: "taller-trabajos", f: "repuestos" },
    { filtro: (s) => s.estado === "LISTA", estado: "Lista para entregar", tono: "lista", accion: "Entregar", pagina: "taller-trabajos", f: "entaller" },
  ];
  resto.forEach((r, i) => solicitudes.filter(r.filtro).forEach((s) => items.push({
    s, orden: 2 + i, clave: new Date(s.fechaCreacion || 0).getTime(),
    estado: r.estado, tono: r.tono, accion: r.accion, pagina: r.pagina, filtro: r.f,
  })));
  return items.sort((a, b) => a.orden - b.orden || a.clave - b.clave);
}
