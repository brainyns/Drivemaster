import { apiFetch } from "./http";

const BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/tiendas/mi-tienda/informes`;

/** Informes de la tienda del usuario (rol TIENDA): ventas, productos, stock, pedidos, despacho y liquidación. */
export async function informesTienda(token, meses = 6) {
  const res = await apiFetch(`${BASE}?meses=${encodeURIComponent(meses)}`, {
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (res.status === 403) throw new Error("Tu tienda aún no está aprobada o activa, así que todavía no hay informes.");
  if (!res.ok) throw new Error("No se pudieron cargar los informes. Intenta de nuevo en unos segundos.");
  return res.json();
}

/** Pregunta al asistente de IA de la tienda. historial: [{ rol: "usuario"|"asistente", texto }]. */
export async function preguntarAsistente(token, mensaje, historial) {
  const res = await apiFetch(BASE.replace(/\/informes$/, "/asistente"), {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ mensaje, historial }),
  });
  const data = await res.json().catch(() => null);
  if (res.status === 429) throw new Error(data?.message || "Hiciste muchas preguntas seguidas. Espera un momento.");
  if (!res.ok) throw new Error(data?.respuesta || "No se pudo consultar al asistente. Intenta de nuevo.");
  return data.respuesta;
}

/** Resumen del día hecho por la IA (el backend lo guarda unas horas: abrir Informes no lo regenera). */
export async function resumenAsistente(token) {
  const res = await apiFetch(BASE.replace(/\/informes$/, "/asistente/resumen"), {
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.message || data?.respuesta || "No se pudo generar el resumen.");
  return data.respuesta;
}
