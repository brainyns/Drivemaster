// Lógica pura de la campanita (sin React ni red): se prueba con node --test.

/** Roles con campanita: el personal y los negocios en la barra del panel; el cliente en la cabecera pública. */
export const ROLES_CON_CAMPANITA = ["SUPERADMIN", "ADMIN", "TALLER", "TIENDA", "CLIENTE"];

export const MAX_AVISOS = 30;

export function tieneCampanita(rol) {
  return ROLES_CON_CAMPANITA.includes(rol);
}

/** "Ahora", "Hace 5 min", "Hace 3 h", "Hace 2 días". */
export function haceCuanto(iso, ahora = Date.now()) {
  if (!iso) return "";
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return "";
  const mins = Math.floor((ahora - t) / 60000);
  if (mins < 1) return "Ahora";
  if (mins < 60) return `Hace ${mins} min`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `Hace ${hrs} h`;
  const dias = Math.floor(hrs / 24);
  return `Hace ${dias} día${dias > 1 ? "s" : ""}`;
}

const TONOS = {
  error: ["PAGO_NO_APLICADO", "PEDIDO_NO_DESPACHADO", "SOLICITUD_CANCELADA", "SIN_TALLER", "PROBLEMA_ENVIO",
    "ALERTA_ADMIN", "COTIZACION_VENCIDA"],
  ok: ["PAGO_APROBADO", "PEDIDO_NUEVO", "VEHICULO_LISTO", "PEDIDO_LISTO_RETIRO", "PROBLEMA_ENVIO_RESUELTO"],
  marca: ["SOLICITUD_NUEVA", "COTIZACION_RESPONDIDA", "SOLICITUD_RETIRADA"],
};

/** Color del punto del aviso: error, ok, marca (naranja) o info. */
export function tonoDeAviso(tipo) {
  for (const [tono, tipos] of Object.entries(TONOS)) if (tipos.includes(tipo)) return tono;
  return "info";
}

/**
 * Página e id a los que lleva un aviso. Las páginas del cliente ("mis-servicios", "mis-compras") abren la
 * solicitud o la compra exacta con su id; los avisos de la tienda abren el pedido con el patrón de las secciones del
 * panel, irA(pagina, { abrir }) (ver destinoSeccion); las demás secciones del panel llevan a la sección.
 */
export function destinoDeAviso(aviso) {
  if (!aviso?.enlace) return null;
  const delCliente = aviso.enlace.startsWith("mis-");
  if (aviso.enlace === "pedidos-tienda") {
    return { pagina: aviso.enlace, id: aviso.referenciaId ? { abrir: aviso.referenciaId } : null };
  }
  return { pagina: aviso.enlace, id: delCliente ? aviso.referenciaId || null : null };
}

/** Agrega un aviso que llegó en vivo al principio, sin duplicarlo y sin pasar del máximo. */
export function agregarAviso(lista, aviso, max = MAX_AVISOS) {
  if (!aviso?.id) return lista;
  return [aviso, ...lista.filter((a) => a.id !== aviso.id)].slice(0, max);
}

export function marcarLeidaEnLista(lista, id) {
  return lista.map((a) => (a.id === id ? { ...a, leida: true } : a));
}

export function marcarTodasEnLista(lista) {
  return lista.map((a) => (a.leida ? a : { ...a, leida: true }));
}

/** Productos activos de la tienda con stock en el mínimo o por debajo (agotados primero). */
export function productosConStockBajo(productos) {
  return (productos || [])
    .filter((p) => p && p.activo !== false && p.stockActual != null && p.stockMinimo != null && p.stockActual <= p.stockMinimo)
    .sort((a, b) => a.stockActual - b.stockActual);
}

/** URL del WebSocket con el ticket de un solo uso (nunca con el token de sesión). */
export function urlWebSocket(base, ticket) {
  const url = new URL(base);
  const proto = url.protocol === "https:" ? "wss" : "ws";
  return `${proto}://${url.host}/ws/notificaciones?ticket=${encodeURIComponent(ticket)}`;
}

/** Espera antes de reconectar: 3 s, 6 s, 9 s… hasta 30 s. */
export function retrasoReconexion(intentos) {
  return Math.min(30000, 3000 * (intentos + 1));
}

/** Texto del contador: más de 99 se muestra como "99+". */
export function textoContador(n) {
  return n > 99 ? "99+" : String(n);
}
