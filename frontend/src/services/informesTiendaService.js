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

/** Resumen del día hecho por la IA (el backend lo guarda unas horas: abrir Informes no lo regenera). */
export async function resumenAsistente(token) {
  const res = await apiFetch(BASE.replace(/\/informes$/, "/asistente/resumen"), {
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.message || data?.respuesta || "No se pudo generar el resumen.");
  return data.respuesta;
}

const SEMANAL = BASE.replace(/\/informes$/, "/asistente/resumen-semanal");
const conToken = (token) => ({ "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) });

async function leer(res, msgDefault) {
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.message || data?.error || msgDefault);
  return data;
}

/** ¿La tienda recibe el resumen semanal por correo? */
export async function resumenSemanal(token) {
  return leer(await apiFetch(SEMANAL, { headers: conToken(token) }), "No se pudo leer la preferencia del resumen semanal.");
}

export async function cambiarResumenSemanal(token, activo) {
  return leer(await apiFetch(SEMANAL, { method: "PUT", headers: conToken(token), body: JSON.stringify({ activo }) }),
    "No se pudo guardar la preferencia.");
}

/** Envía ya el resumen de la semana pasada al correo del dueño. */
export async function probarResumenSemanal(token) {
  return leer(await apiFetch(`${SEMANAL}/prueba`, { method: "POST", headers: conToken(token) }),
    "No se pudo enviar el correo de prueba.");
}

const ASISTENTE = BASE.replace(/\/informes$/, "/asistente");

/**
 * Pregunta al asistente de la tienda. historial: [{ rol: "usuario"|"asistente", texto }].
 * Devuelve { respuesta, error, detras } (detras: seguridad, modelo, tokens, ms, herramienta y datos usados).
 */
export async function preguntarAsistente(token, mensaje, historial) {
  return leer(await apiFetch(ASISTENTE, { method: "POST", headers: conToken(token), body: JSON.stringify({ mensaje, historial }) }),
    "No se pudo consultar al asistente. Intenta de nuevo.");
}

/** Sugerencias de la IA para el formulario de producto: { categoria, compatibilidades, nota }. No guarda nada. */
export async function sugerirProducto(token, { nombre, marca, referencia }, categorias) {
  return leer(await apiFetch(`${ASISTENTE}/sugerir-producto`, {
    method: "POST", headers: conToken(token), body: JSON.stringify({ nombre, marca, referencia, categorias }),
  }), "No se pudieron obtener sugerencias.");
}
