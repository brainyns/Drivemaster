import { apiFetch } from "./http";

const API = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/notificaciones`;

async function json(res) {
  if (!res.ok) {
    const error = new Error(`HTTP ${res.status}`);
    error.status = res.status;
    throw error;
  }
  return res.status === 204 ? null : res.json();
}

/** Avisos propios, el más reciente primero: { items, noLeidas }. */
export async function listarMisAvisos(limite = 30) {
  return json(await apiFetch(`${API}/mias?limite=${limite}`));
}

export async function marcarAvisoLeido(id) {
  return json(await apiFetch(`${API}/${encodeURIComponent(id)}/leida`, { method: "PATCH" }));
}

export async function marcarTodosLeidos() {
  return json(await apiFetch(`${API}/mias/leidas`, { method: "PATCH" }));
}

/** Ticket de un solo uso (30 s) para abrir el WebSocket. */
export async function pedirTicket() {
  const data = await json(await apiFetch(`${API}/ticket`, { method: "POST" }));
  return data.ticket;
}
