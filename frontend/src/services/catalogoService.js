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
