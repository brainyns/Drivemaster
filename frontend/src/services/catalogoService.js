import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/catalogo`;

async function handle(res, msgDefault) {
  if (!res.ok) {
    let msg = msgDefault;
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

// ─── Consulta pública (con sesión de cliente + vehiculoId, avisa la compatibilidad) ───

/** Catálogo multi-tienda. params: q, ciudad, tiendaId, categoria, vehiculoId, orden, pagina, tamanio */
export async function buscarProductos(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== "") query.set(k, String(v).trim());
  });
  const res = await apiFetch(`${BASE}/productos?${query.toString()}`);
  return handle(res, "No se pudo cargar el catálogo");
}

/** Detalle con el mismo repuesto en otras tiendas (otrasTiendas). */
export async function detalleProducto(id, vehiculoId) {
  const query = vehiculoId ? `?vehiculoId=${encodeURIComponent(vehiculoId)}` : "";
  const res = await apiFetch(`${BASE}/productos/${encodeURIComponent(id)}${query}`);
  return handle(res, "No se pudo cargar el producto");
}

/** Ciudades, tiendas y categorías con catálogo, para los filtros. */
export async function filtrosCatalogo() {
  const res = await apiFetch(`${BASE}/filtros`);
  return handle(res, "No se pudieron cargar los filtros");
}

// ─── Compra directa (CLIENTE) ────────────────────────────────────────────────
// El navegador manda solo producto, cantidad, método de entrega y dirección: los precios, el IVA y el
// envío los calcula siempre el backend.

function headers(token) {
  const h = { "Content-Type": "application/json" };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

function post(token, ruta, body, msg) {
  return apiFetch(`${BASE}${ruta}`, { method: "POST", headers: headers(token), body: JSON.stringify(body ?? {}) })
    .then((res) => handle(res, msg));
}

/**
 * solicitud: { items: [{productoId, cantidad}], entregas: [{tiendaId, tipo: "ENVIO"|"RETIRO"}],
 *              envio?: {receptor, telefono, direccion, ciudad, indicaciones}, vehiculoId? }
 */
export function cotizarCompra(token, solicitud) {
  return post(token, "/compras/cotizar", solicitud, "No se pudo calcular la compra");
}

/** Revisa el stock de todas las tiendas y devuelve el link de pago de Wompi (más compraId y verificacionManual). */
export function iniciarPagoCompra(token, solicitud) {
  return post(token, "/compras", solicitud, "No se pudo iniciar el pago");
}

export async function misCompras(token) {
  const res = await apiFetch(`${BASE}/compras/mias`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar tus compras");
}

/** Pide un código de retiro nuevo: llega por correo, nunca en la respuesta. */
export function pedirCodigoRetiro(token, pedidoId) {
  return post(token, `/pedidos/${encodeURIComponent(pedidoId)}/codigo-retiro`, {}, "No se pudo generar el código");
}

export function confirmarRecepcion(token, pedidoId) {
  return post(token, `/pedidos/${encodeURIComponent(pedidoId)}/recibido`, {}, "No se pudo confirmar la recepción");
}

export function reportarProblema(token, pedidoId, detalle) {
  return post(token, `/pedidos/${encodeURIComponent(pedidoId)}/problema`, { detalle }, "No se pudo enviar el reporte");
}

/**
 * Trae TODAS las páginas de una consulta del catálogo (tope de seguridad: 10 páginas de 60). La vitrina
 * principal filtra marca y precio en el navegador, así que necesita el conjunto completo.
 */
export async function cargarTodosLosProductos(params = {}) {
  const TAMANIO = 60;
  const MAX_PAGINAS = 10;
  const items = [];
  let total = 0;
  for (let pagina = 0; pagina < MAX_PAGINAS; pagina++) {
    const r = await buscarProductos({ ...params, pagina, tamanio: TAMANIO });
    items.push(...r.items);
    total = r.total;
    if (items.length >= total || r.items.length === 0) break;
  }
  return { items, total };
}
