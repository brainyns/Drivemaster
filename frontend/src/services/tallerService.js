import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/talleres`;

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

export async function talleresActivos(token, { ciudad, lat, lng } = {}) {
  const params = new URLSearchParams();
  if (ciudad) params.set("ciudad", ciudad);
  if (lat != null && lng != null) {
    params.set("lat", lat);
    params.set("lng", lng);
  }
  const qs = params.toString();
  const res = await apiFetch(`${BASE}/activos${qs ? `?${qs}` : ""}`, { headers: headers(token) });
  return handle(res, "Error al listar los talleres");
}

export async function listarTalleres(token) {
  const res = await apiFetch(BASE, { headers: headers(token) });
  return handle(res, "Error al listar los talleres");
}

export async function crearTaller(token, taller) {
  const res = await apiFetch(BASE, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(taller),
  });
  return handle(res, "Error al crear el taller");
}

export async function actualizarTaller(token, id, taller) {
  const res = await apiFetch(`${BASE}/${id}`, {
    method: "PUT",
    headers: headers(token),
    body: JSON.stringify(taller),
  });
  return handle(res, "Error al actualizar el taller");
}

export async function activarDesactivarTaller(token, id, activo) {
  const res = await apiFetch(`${BASE}/${id}/activo`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ activo }),
  });
  return handle(res, "Error al cambiar el estado del taller");
}
