import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/suscripciones`;

function headers(token) {
  const h = { "Content-Type": "application/json" };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

async function handle(res, msgDefault) {
  if (!res.ok) {
    let msg = msgDefault;
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

export async function listarPlanes(token) {
  const res = await apiFetch(`${BASE}/planes`, { headers: headers(token) });
  return handle(res, "Error al cargar los planes");
}

/** Activa un plan para un negocio ya aprobado. actorTipo: TALLER | TIENDA. */
export async function activarPlan(token, actorTipo, actorId, planCodigo) {
  const res = await apiFetch(BASE, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ actorTipo, actorId, planCodigo }),
  });
  return handle(res, "Error al activar el plan");
}
