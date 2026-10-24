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

/** Franjas del horario del taller ({ diaSemana, abierto, horaApertura, horaCierre, orden }); [] si no lo ha configurado. */
export async function obtenerHorario(token, tallerId) {
  const res = await apiFetch(`${BASE}/${encodeURIComponent(tallerId)}/horario`, { headers: headers(token) });
  return handle(res, "No se pudo cargar el horario");
}

/** Reemplaza el horario completo; el backend valida cierres y cruces y responde 400 con el día que falla. */
export async function guardarHorario(token, tallerId, franjas) {
  const res = await apiFetch(`${BASE}/${encodeURIComponent(tallerId)}/horario`, {
    method: "PUT",
    headers: headers(token),
    body: JSON.stringify(franjas),
  });
  if (res.status === 403) throw new Error("Tu taller aún no está aprobado, así que todavía no puedes editar el horario.");
  return handle(res, "No se pudo guardar el horario");
}
