import { apiFetch } from "./http";

const BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/inventario-taller`;

function headers(token) {
  return { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) };
}

async function respuesta(res, mensaje) {
  if (res.ok) return res.json();
  let msg = mensaje;
  try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* sin cuerpo */ }
  throw new Error(msg);
}

/** Mi inventario (rol TALLER): piezas con costo y margen, y lo comprado que aún no llega. Solo lo ve el taller dueño. */
export async function miInventario(token) {
  return respuesta(await apiFetch(BASE, { headers: headers(token) }), "No se pudo cargar tu inventario.");
}

/** Precio de venta antes de IVA. Devuelve la pieza con sus cifras recalculadas. */
export async function cambiarPrecioPieza(token, piezaId, precioVenta) {
  const res = await apiFetch(`${BASE}/piezas/precio?piezaId=${encodeURIComponent(piezaId)}`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ precioVenta }),
  });
  return respuesta(res, "No se pudo guardar el precio.");
}

/** El pedido llegó al taller: sus piezas entran al inventario. Devuelve el inventario actualizado. */
export async function confirmarLlegada(token, pedidoId) {
  const res = await apiFetch(`${BASE}/pedidos/${encodeURIComponent(pedidoId)}/llego`, { method: "POST", headers: headers(token) });
  return respuesta(res, "No se pudo confirmar la llegada.");
}
