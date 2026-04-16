const BASE_URL = "http://localhost:8080/api/movimientos";

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

  const res = await fetch(`${BASE_URL}?${params}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener movimientos");
  return res.json();
}

export async function registrarMovimiento({ productoId, tipo, cantidad, motivo }, token) {
  const params = new URLSearchParams({ productoId, tipo, cantidad, motivo });
  const res = await fetch(`${BASE_URL}/registrar?${params}`, {
    method: "POST",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al registrar movimiento");
  return res.json();
}