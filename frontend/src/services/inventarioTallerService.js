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

/** Mínimo para la alerta de stock bajo (null la apaga). Devuelve el inventario actualizado. */
export async function cambiarMinimoPieza(token, piezaId, minimo) {
  const res = await apiFetch(`${BASE}/piezas/minimo?piezaId=${encodeURIComponent(piezaId)}`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ minimo }),
  });
  return respuesta(res, "No se pudo guardar el mínimo.");
}

/**
 * Movimientos del inventario del taller: { movimientos: Pagina, resumen }. Parámetros: tipo (ENTRADA, SALIDA, AJUSTE),
 * q (pieza), desde, hasta (AAAA-MM-DD), page, size y sort ("fecha,desc").
 */
export async function movimientosTaller(token, parametros = {}) {
  const q = new URLSearchParams();
  Object.entries(parametros).forEach(([k, v]) => { if (v !== undefined && v !== null && String(v).trim() !== "") q.set(k, String(v).trim()); });
  return respuesta(await apiFetch(`${BASE}/movimientos?${q}`, { headers: headers(token) }), "No se pudieron cargar los movimientos.");
}

/** Ajuste, carga inicial, merma o devolución: { clase, piezaId | productoId, cantidad, costo, motivo }. */
export async function registrarMovimientoTaller(token, movimiento) {
  const res = await apiFetch(`${BASE}/movimientos`, { method: "POST", headers: headers(token), body: JSON.stringify(movimiento) });
  return respuesta(res, "No se pudo registrar el movimiento. Inténtalo de nuevo.");
}

/** Descarga en Excel o PDF exactamente lo que filtra la pantalla (lo arma el backend). */
export async function exportarMovimientosTaller(token, { busqueda = "", tipo = "", desde = "", hasta = "" } = {}, formato) {
  const q = new URLSearchParams({ formato });
  if (busqueda.trim()) q.set("q", busqueda.trim());
  if (tipo) q.set("tipo", tipo);
  if (desde) q.set("desde", desde);
  if (hasta) q.set("hasta", hasta);
  const res = await apiFetch(`${BASE}/movimientos/exportar?${q}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) await respuesta(res, "No pudimos generar el archivo.");
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = `Movimientos-mi-inventario-${new Date().toISOString().slice(0, 10)}.${formato === "pdf" ? "pdf" : "xlsx"}`;
  a.click();
  URL.revokeObjectURL(url);
}
