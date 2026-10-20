import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

const headers = (token) => ({
  "Content-Type": "application/json",
  ...(token && { Authorization: `Bearer ${token}` }),
});

/** Ventas del cliente con sesión: compras del flujo anterior (el mismo endpoint del Historial de compras). */
export async function misVentas(token) {
  const res = await apiFetch(`${BASE}/ventas/mis-ventas`, { headers: headers(token) });
  if (!res.ok) throw new Error("No se pudieron cargar tus compras anteriores");
  return res.json();
}

export async function cambiarEstadoVenta(id, estado, token) {
  const res = await apiFetch(`${BASE}/ventas/${id}/estado`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ estado }),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "Error al cambiar el estado");
  }
  return res.json();
}
