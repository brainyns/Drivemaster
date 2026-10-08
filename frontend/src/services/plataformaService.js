import { apiFetch } from "./http";

const BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/plataforma`;

function headers(token) {
  const h = { "Content-Type": "application/json" };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

/** Cifras del panel de la plataforma (solo SUPERADMIN). */
export async function dashboardPlataforma(token) {
  const res = await apiFetch(`${BASE}/dashboard`, { headers: headers(token) });
  if (!res.ok) throw new Error(res.status === 403 ? "No tienes permiso para ver el panel de la plataforma." : "No se pudo cargar el panel.");
  return res.json();
}
