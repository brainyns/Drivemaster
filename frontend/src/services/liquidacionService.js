import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/liquidaciones`;

function headers(token) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

async function handle(res, msgDefault) {
  if (!res.ok) {
    let msg = msgDefault;
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.json();
}

export async function listarRepartos(token, estado) {
  const res = await apiFetch(`${BASE}/repartos${estado ? `?estado=${estado}` : ""}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar las liquidaciones");
}

/** Modo de dispersión del backend: { modo: "SIMULADO" | "MANUAL", automatica } */
export async function obtenerModo(token) {
  const res = await apiFetch(`${BASE}/modo`, { headers: headers(token) });
  return handle(res, "No se pudo consultar el modo de liquidación");
}

/** En modo MANUAL el número de transferencia es obligatorio; en SIMULADO el backend lo ignora. */
export async function liquidarReparto(token, id, { numeroTransferencia, nota } = {}) {
  const res = await apiFetch(`${BASE}/repartos/${id}/liquidar`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ numeroTransferencia, nota }),
  });
  return handle(res, "No se pudo liquidar");
}

export async function listarReembolsos(token, estado) {
  const res = await apiFetch(`${BASE}/reembolsos${estado ? `?estado=${estado}` : ""}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar los reembolsos");
}

export async function marcarReembolsado(token, id, nota) {
  const res = await apiFetch(`${BASE}/reembolsos/${id}/realizado`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ nota }),
  });
  return handle(res, "No se pudo marcar como reembolsado");
}
