// Estados y formatos compartidos por las pantallas del flujo de solicitudes
// (cliente y taller). Archivo sin componentes para no romper Fast Refresh.

export const ESTADOS = {
  ENVIADA:             { label: "Enviada",               tono: "info" },
  ASIGNADA_A_TALLER:   { label: "Asignada a taller",     tono: "info" },
  REASIGNADA:          { label: "Buscando otro taller",  tono: "aviso" },
  EN_REVISION:         { label: "En revisión",           tono: "proceso" },
  DIAGNOSTICADA:       { label: "Diagnosticada",         tono: "proceso" },
  COTIZADA:            { label: "Cotizada",              tono: "aviso" },
  APROBADA:            { label: "Aprobada",              tono: "proceso" },
  PAGADA:              { label: "Pagada",                tono: "ok" },
  REPUESTOS_PEDIDOS:   { label: "Repuestos pedidos",     tono: "proceso" },
  REPUESTOS_RECIBIDOS: { label: "Repuestos recibidos",   tono: "proceso" },
  EN_INSTALACION:      { label: "En instalación",        tono: "proceso" },
  LISTA:               { label: "Lista para entregar",   tono: "ok" },
  ENTREGADA:           { label: "Entregada",             tono: "ok" },
  RECHAZADA:           { label: "Sin taller disponible", tono: "error" },
  CANCELADA:           { label: "Cancelada",             tono: "error" },
};

export const URGENCIAS = [
  { valor: "BAJA",    label: "Baja",    detalle: "Puede esperar" },
  { valor: "MEDIA",   label: "Media",   detalle: "Atender pronto" },
  { valor: "ALTA",    label: "Alta",    detalle: "Afecta el uso" },
  { valor: "CRITICA", label: "Crítica", detalle: "No circular" },
];

// Estado de cada oferta del buscador; lo calcula el backend.
export const ESTADO_OFERTA = {
  COMPATIBLE:    { label: "Compatible",                   tono: "ok" },
  NO_COMPATIBLE: { label: "No compatible",                tono: "error" },
  AGOTADO:       { label: "Agotado",                      tono: "error" },
  POR_CONFIRMAR: { label: "Compatibilidad por confirmar", tono: "aviso" },
};

export const TEXTO_CONFIRMACION = "Confirmo que este repuesto es compatible con el vehículo";

// ─── Fase 3: cotización, pedidos a tiendas y liquidaciones ───

export const ESTADOS_COTIZACION = {
  EMITIDA:             { label: "Esperando respuesta",  tono: "aviso" },
  APROBADA:            { label: "Aprobada",             tono: "ok" },
  RECHAZADA:           { label: "Rechazada",            tono: "error" },
  CAMBIOS_SOLICITADOS: { label: "Cambios solicitados",  tono: "aviso" },
  VENCIDA:             { label: "Vencida",              tono: "error" },
  REEMPLAZADA:         { label: "Reemplazada",          tono: "info" },
  ANULADA:             { label: "Anulada (sin stock)",  tono: "error" },
};

export const ESTADOS_PEDIDO = {
  PENDIENTE:     { label: "Por despachar",     tono: "aviso" },
  EN_PREPARACION: { label: "En preparación",   tono: "proceso" },
  DESPACHADO:    { label: "Despachado",        tono: "proceso" },
  NO_DESPACHADO: { label: "No se despachó",    tono: "error" },
  RECIBIDO:      { label: "Recibido en taller", tono: "ok" },
  // Compras directas del catálogo
  LISTO_PARA_RETIRAR: { label: "Listo para retirar", tono: "ok" },
  RETIRADO:      { label: "Retirado",          tono: "ok" },
  ENTREGADO:     { label: "Entregado",         tono: "ok" },
  NO_ENTREGADO:  { label: "No entregado",      tono: "error" },
};

export const MOTIVOS_NO_DESPACHO = [
  { valor: "SIN_REPUESTO", label: "No tengo el repuesto" },
  { valor: "OTRO",         label: "Otro motivo" },
];

// Estados del taller después de diagnosticar, hasta la entrega
export const ESTADOS_EN_CURSO = [
  "COTIZADA", "APROBADA", "PAGADA", "REPUESTOS_PEDIDOS", "REPUESTOS_RECIBIDOS", "EN_INSTALACION", "LISTA",
];

export const ESTADOS_CANCELABLES_CLIENTE =["ENVIADA", "ASIGNADA_A_TALLER", "REASIGNADA"];

export const formatPrecio = (v) =>
  v == null ? "—" : new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(v);

export const formatFechaHora = (f) =>
  f ? new Date(f).toLocaleString("es-CO", { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }) : "—";

export const formatFecha = (f) =>
  f ? new Date(f).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" }) : "—";

/** Valor mínimo para un <input type="datetime-local"> (ahora, en hora local). */
export function ahoraParaInput() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
}

export const refSolicitud = (id) => (id ? id.slice(-6).toUpperCase() : "—");
