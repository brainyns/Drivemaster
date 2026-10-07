import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/solicitudes-servicio`;

// Solicitudes de diagnóstico del marketplace. No confundir con solicitudService
// (pedidos de productos por encargo).

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

function patch(token, url, body, msg) {
  return apiFetch(url, { method: "PATCH", headers: headers(token), body: JSON.stringify(body || {}) })
    .then(res => handle(res, msg));
}

// ─── Cliente ───────────────────────────────────────────────────────────────

export async function crearSolicitudServicio(token, datos) {
  const res = await apiFetch(BASE, { method: "POST", headers: headers(token), body: JSON.stringify(datos) });
  return handle(res, "No se pudo enviar la solicitud");
}

/** Sube una foto (cliente o taller) y devuelve su URL. */
export async function subirFotoServicio(token, archivo) {
  const form = new FormData();
  form.append("archivo", archivo);
  const res = await apiFetch(`${BASE}/fotos`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  const data = await handle(res, "No se pudo subir la foto");
  return data.url;
}

export async function misSolicitudesServicio(token) {
  const res = await apiFetch(`${BASE}/mias`, { headers: headers(token) });
  return handle(res, "Error al cargar tus servicios");
}

export async function obtenerSolicitudServicio(token, id) {
  const res = await apiFetch(`${BASE}/${id}`, { headers: headers(token) });
  return handle(res, "Solicitud no encontrada");
}

export function cancelarSolicitudServicio(token, id, motivo) {
  return patch(token, `${BASE}/${id}/cancelar`, { motivo }, "No se pudo cancelar la solicitud");
}

// ─── Taller ────────────────────────────────────────────────────────────────

export async function bandejaSolicitudesTaller(token) {
  const res = await apiFetch(`${BASE}/taller`, { headers: headers(token) });
  return handle(res, "Error al cargar las solicitudes");
}

/** datos: { fechaRevision: "2026-10-08T10:00", mecanicoId? } */
export function aceptarSolicitudServicio(token, id, datos) {
  return patch(token, `${BASE}/${id}/aceptar`, datos, "No se pudo aceptar la solicitud");
}

export function rechazarSolicitudServicio(token, id, motivo) {
  return patch(token, `${BASE}/${id}/rechazar`, { motivo }, "No se pudo rechazar la solicitud");
}

export function asignarMecanicoSolicitud(token, id, mecanicoId) {
  return patch(token, `${BASE}/${id}/mecanico`, { mecanicoId }, "No se pudo asignar el mecánico");
}

export async function registrarDiagnostico(token, id, datos) {
  const res = await apiFetch(`${BASE}/${id}/diagnostico`, {
    method: "PUT", headers: headers(token), body: JSON.stringify(datos),
  });
  return handle(res, "No se pudo guardar el diagnóstico");
}

/** items: [{ descripcion, cantidad, productoId }] — reemplaza la selección completa. */
export async function guardarRepuestosSolicitud(token, id, items) {
  const res = await apiFetch(`${BASE}/${id}/repuestos`, {
    method: "PUT", headers: headers(token), body: JSON.stringify({ items }),
  });
  return handle(res, "No se pudieron guardar los repuestos");
}

// ─── Etapas finales (taller asignado) ─────────────────────────────────────

export function iniciarInstalacion(token, id) {
  return patch(token, `${BASE}/${id}/iniciar-instalacion`, null, "No se pudo iniciar la instalación");
}

export function marcarEnPruebas(token, id) {
  return patch(token, `${BASE}/${id}/en-pruebas`, null, "No se pudo marcar en pruebas");
}

export function marcarLista(token, id) {
  return patch(token, `${BASE}/${id}/lista`, null, "No se pudo marcar como lista");
}

export function marcarEntregada(token, id) {
  return patch(token, `${BASE}/${id}/entregada`, null, "No se pudo marcar la entrega");
}

// ─── Cliente: recepción ────────────────────────────────────────────────────

/** calificacion: 1 a 5 u omitida; comentario opcional */
export function confirmarRecepcion(token, id, calificacion, comentario) {
  return patch(token, `${BASE}/${id}/confirmar-recepcion`, { calificacion, comentario }, "No se pudo confirmar la recepción");
}
