import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/repuestos`;

/**
 * Buscador multi-tienda del taller, evaluado contra el vehículo de la orden.
 * Devuelve repuestos agrupados con sus ofertas por tienda. Cada oferta trae su
 * estado (COMPATIBLE, NO_COMPATIBLE, AGOTADO, POR_CONFIRMAR), si se puede elegir
 * y por qué no. soloCompatibles=false incluye también lo no compatible y lo
 * pendiente de confirmar.
 */
export async function buscarRepuestos(token, { ordenId, q, referencia, cantidad, soloCompatibles = true }) {
  const params = new URLSearchParams({ ordenId, soloCompatibles: String(soloCompatibles) });
  if (q) params.set("q", q);
  if (referencia) params.set("referencia", referencia);
  if (cantidad) params.set("cantidad", cantidad);

  const res = await apiFetch(`${BASE}/buscar?${params}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    let msg = "Error al buscar repuestos";
    try { const data = await res.json(); msg = data.error || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.json();
}
