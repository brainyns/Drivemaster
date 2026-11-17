import { apiFetch } from "./http";

const BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/talleres/mi-taller/informes`;

/** Informes del taller del usuario (rol TALLER): servicios, ingresos, tiempo de respuesta y calificación. */
export async function informesTaller(token, meses = 6) {
  const res = await apiFetch(`${BASE}?meses=${encodeURIComponent(meses)}`, {
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (res.status === 403) throw new Error("Tu taller aún no está aprobado, así que todavía no hay informes.");
  if (!res.ok) throw new Error("No se pudieron cargar los informes. Intenta de nuevo en unos segundos.");
  return res.json();
}

/**
 * Pregunta al asistente del taller. historial: [{ rol: "usuario"|"asistente", texto }].
 * Devuelve { respuesta, error, detras }, igual que el de la tienda.
 */
export async function preguntarAsistenteTaller(token, mensaje, historial) {
  const res = await apiFetch(BASE.replace(/\/informes$/, "/asistente"), {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ mensaje, historial }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error || "No se pudo consultar al asistente. Intenta de nuevo.");
  return data;
}
