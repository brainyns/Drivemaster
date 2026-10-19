import { apiFetch } from "./http";
import { errorDeRespuesta } from "../utils/errores";
const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/movimientos`;

const buildHeaders = (token, contentType) => {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (contentType) headers["Content-Type"] = contentType;
  return headers;
};

export async function listarMovimientos({ productoId, tipo, fechaInicio, fechaFin } = {}, token) {
  const params = new URLSearchParams();
  if (productoId)  params.append("productoId", productoId);
  if (tipo)        params.append("tipo", tipo);
  if (fechaInicio) params.append("fechaInicio", fechaInicio);
  if (fechaFin)    params.append("fechaFin", fechaFin);

  const res = await apiFetch(`${BASE_URL}?${params}`, { headers: buildHeaders(token) });
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudieron cargar los movimientos");
  return res.json();
}

export async function registrarMovimiento({ productoId, tipo, cantidad, motivo }, token) {
  const params = new URLSearchParams({ productoId, tipo, cantidad, motivo });
  const res = await apiFetch(`${BASE_URL}/registrar?${params}`, {
    method: "POST",
    headers: buildHeaders(token),
  });
  // El mensaje real del backend (p. ej. stock insuficiente); el genérico solo si la respuesta no trae cuerpo
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudo registrar el movimiento. Inténtalo de nuevo.");
  return res.json();
}