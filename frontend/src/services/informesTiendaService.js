import { apiFetch } from "./http";

const BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/tiendas/mi-tienda/informes`;

/** Informes de la tienda del usuario (rol TIENDA): ventas, productos, stock, pedidos, despacho y liquidación. */
export async function informesTienda(token, meses = 6) {
  const res = await apiFetch(`${BASE}?meses=${encodeURIComponent(meses)}`, {
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (res.status === 403) throw new Error("Tu tienda aún no está aprobada o activa, así que todavía no hay informes.");
  if (!res.ok) throw new Error("No se pudieron cargar los informes. Intenta de nuevo en unos segundos.");
  return res.json();
}
