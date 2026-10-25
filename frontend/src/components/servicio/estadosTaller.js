// Vocabulario y colores de estado del panel del taller (Inicio, Solicitudes, Trabajos, Cotizaciones e Informes).
// Cada estado del backend cae en una de seis fases y cada fase tiene un color propio (tokens --fase-* de
// global.css). El cliente sigue usando ESTADOS de estadosServicio.js. Archivo sin componentes (Fast Refresh).

/** Las seis fases, en el orden del flujo. tono es la variante de ChipEstado (ui-badge--fase-*). */
export const FASES = {
  nueva:      { label: "Solicitud nueva",       tono: "nueva" },
  cotizacion: { label: "Cotización pendiente",  tono: "cotizacion" },
  curso:      { label: "En curso",              tono: "curso" },
  lista:      { label: "Lista para entregar",   tono: "lista" },
  entregada:  { label: "Entregada",             tono: "entregada" },
  cancelada:  { label: "Cancelada",             tono: "cancelada" },
};

const FASE_DE = {
  ENVIADA: "nueva", ASIGNADA_A_TALLER: "nueva", EN_REVISION: "nueva",
  DIAGNOSTICADA: "cotizacion", COTIZADA: "cotizacion", APROBADA: "cotizacion",
  PAGADA: "curso", REPUESTOS_PEDIDOS: "curso", REPUESTOS_RECIBIDOS: "curso", EN_INSTALACION: "curso",
  LISTA: "lista",
  ENTREGADA: "entregada",
  CANCELADA: "cancelada", RECHAZADA: "cancelada", REASIGNADA: "cancelada",
};

/** Fase de un estado del backend ("nueva" si no se conoce). */
export const faseDe = (estado) => FASE_DE[estado] || "nueva";

/** Nombre de cada estado en el panel del taller: el mismo en chips, historial, pestañas e informes. */
export const ETIQUETAS_TALLER = {
  ENVIADA: "Solicitud nueva",
  ASIGNADA_A_TALLER: "Solicitud nueva",
  EN_REVISION: "En revisión",
  DIAGNOSTICADA: "Por cotizar",
  COTIZADA: "Cotización enviada",
  APROBADA: "Esperando pago",
  PAGADA: "Pagada",
  REPUESTOS_PEDIDOS: "Esperando repuestos",
  REPUESTOS_RECIBIDOS: "Repuestos recibidos",
  EN_INSTALACION: "En instalación",
  LISTA: "Lista para entregar",
  ENTREGADA: "Entregada",
  CANCELADA: "Cancelada",
  RECHAZADA: "Sin taller disponible",
  REASIGNADA: "Pasó a otro taller",
};

/** Catálogo para ChipEstado ({ label, tono } por estado), con el tono de su fase. */
export const ESTADOS_TALLER = Object.fromEntries(
  Object.entries(ETIQUETAS_TALLER).map(([estado, label]) => [estado, { label, tono: faseDe(estado) }])
);

/** Estado a mostrar de una solicitud: aceptada y sin diagnosticar es una revisión agendada, no una solicitud nueva. */
export function estadoDeSolicitud(s) {
  if (s.estado === "ASIGNADA_A_TALLER" && s.aceptadaPorTaller) return { label: "Revisión agendada", tono: "nueva" };
  return ESTADOS_TALLER[s.estado] || { label: s.estado, tono: "nueva" };
}

/** Cotizaciones del taller con la misma paleta: pendiente en ámbar, aprobada en curso y los cierres en gris. */
export const ESTADOS_COTIZACION_TALLER = {
  EMITIDA:             { label: "Pendiente",           tono: "cotizacion" },
  CAMBIOS_SOLICITADOS: { label: "Cambios solicitados", tono: "cotizacion" },
  APROBADA:            { label: "Aprobada",            tono: "curso" },
  RECHAZADA:           { label: "Rechazada",           tono: "cancelada" },
  VENCIDA:             { label: "Vencida",             tono: "cancelada" },
  REEMPLAZADA:         { label: "Reemplazada",         tono: "cancelada" },
  ANULADA:             { label: "Anulada (sin stock)", tono: "cancelada" },
};
