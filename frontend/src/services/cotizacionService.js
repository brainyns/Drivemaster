import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/cotizaciones`;

// Cotizaciones del marketplace. Las funciones *Publica usan el token del correo:
// va solo en el cuerpo del POST (nunca en la URL) y no se guarda en ningún lado.

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

function post(token, url, body, msg) {
  return apiFetch(url, { method: "POST", headers: headers(token), body: JSON.stringify(body || {}) })
    .then((res) => handle(res, msg));
}

// ─── Taller ────────────────────────────────────────────────────────────────

/** datos: { manoObra: [{ descripcion, modalidad: "ITEM"|"HORAS", valor?, horas? }], tiempoEstimadoHoras?, observaciones? } */
export function previsualizarCotizacion(token, ordenId, datos) {
  return post(token, `${BASE}/orden/${ordenId}/previsualizar`, datos, "No se pudo calcular la cotización");
}

export function emitirCotizacion(token, ordenId, datos) {
  return post(token, `${BASE}/orden/${ordenId}`, datos, "No se pudo emitir la cotización");
}

// ─── Con sesión (cliente, taller, ADMIN) ───────────────────────────────────

export async function cotizacionesDeOrden(token, ordenId) {
  const res = await apiFetch(`${BASE}/orden/${ordenId}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar las cotizaciones");
}

/** accion: APROBAR | RECHAZAR | CAMBIOS (motivo obligatorio en CAMBIOS) */
export function responderCotizacion(token, cotizacionId, accion, motivo) {
  return post(token, `${BASE}/${cotizacionId}/responder`, { accion, motivo }, "No se pudo enviar tu respuesta");
}

// ─── Enlace del correo (sin sesión) ────────────────────────────────────────

export async function consultarCotizacionPublica(tokenEnlace) {
  const res = await fetch(`${BASE}/publica/consultar`, {
    method: "POST", headers: headers(null), body: JSON.stringify({ token: tokenEnlace }),
  });
  return handle(res, "Enlace no válido o vencido");
}

export async function responderCotizacionPublica(tokenEnlace, accion, motivo) {
  const res = await fetch(`${BASE}/publica/responder`, {
    method: "POST", headers: headers(null), body: JSON.stringify({ token: tokenEnlace, accion, motivo }),
  });
  return handle(res, "No se pudo enviar tu respuesta");
}

/** Todas las cotizaciones del taller de la sesión, la más reciente primero. */
export async function cotizacionesDelTaller(token) {
  const res = await apiFetch(`${BASE}/taller`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar las cotizaciones");
}
