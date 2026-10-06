import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/tiendas`;

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

/** Registro público de una tienda. Queda pendiente hasta que DriveMaster la apruebe. */
export async function registrarTienda(datos) {
  const res = await fetch(`${BASE}/registro`, {
    method: "POST",
    headers: headers(null),
    body: JSON.stringify(datos),
  });
  return handle(res, "No se pudo registrar la tienda");
}

export async function miTienda(token) {
  const res = await apiFetch(`${BASE}/mi-tienda`, { headers: headers(token) });
  return handle(res, "Error al cargar los datos de la tienda");
}

/** Todas las tiendas, o solo las de un estado de revisión (PENDIENTE, APROBADO, RECHAZADO). */
export async function listarTiendas(token, estado) {
  const res = await apiFetch(`${BASE}${estado ? `?estado=${encodeURIComponent(estado)}` : ""}`, { headers: headers(token) });
  return handle(res, "Error al listar las tiendas");
}

/** Body: { aprobado, motivo?, planCodigo? } */
export async function resolverAprobacionTienda(token, id, decision) {
  const res = await apiFetch(`${BASE}/${id}/aprobacion`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify(decision),
  });
  return handle(res, "Error al registrar la decisión");
}

export async function activarDesactivarTienda(token, id, activo) {
  const res = await apiFetch(`${BASE}/${id}/activo`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ activo }),
  });
  return handle(res, "Error al cambiar el estado de la tienda");
}
