import { apiFetch } from "./http";
import { errorDeRespuesta } from "../utils/errores";
const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/compras`;

const buildHeaders = (token, contentType = "application/json") => {
  const headers = {};
  if (contentType) headers["Content-Type"] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function listarCompras(token) {
  const res = await apiFetch(BASE_URL, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener compras");
  return res.json();
}

export async function obtenerCompra(id, token) {
  const res = await apiFetch(`${BASE_URL}/${id}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Compra no encontrada");
  return res.json();
}

export async function crearCompra(compra, token) {
  const res = await apiFetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(compra),
  });
  // Un 400 trae el error de cada campo (error.campos) para mostrarlo en el formulario
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudo registrar la compra");
  return res.json();
}

/** La mercancía de una compra pendiente llegó: pasa a recibida y se suma el stock. */
export async function recibirCompra(id, token) {
  const res = await apiFetch(`${BASE_URL}/${id}/recibir`, { method: "PATCH", headers: buildHeaders(token) });
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudo marcar la compra como recibida");
  return res.json();
}

/** Una compra pendiente no va a llegar: pasa a cancelada (motivo opcional) sin tocar el stock. */
export async function cancelarCompra(id, motivo, token) {
  const res = await apiFetch(`${BASE_URL}/${id}/cancelar`, {
    method: "PATCH", headers: buildHeaders(token), body: JSON.stringify({ motivo: motivo || null }),
  });
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudo cancelar la compra");
  return res.json();
}

export async function descargarPdfCompra(id, token) {
  const res = await apiFetch(`${BASE_URL}/${id}/pdf`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al generar el PDF de la compra");
  const blob = await res.blob();
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement("a");
  a.href     = url;
  a.download = `Factura-Compra-PO-${id.slice(-5).toUpperCase()}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
}