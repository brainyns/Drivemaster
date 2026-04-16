const BASE_URL = "http://localhost:8080/api/ventas";

const buildHeaders = (token, contentType = "application/json") => {
  const headers = {};
  if (contentType) headers["Content-Type"] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function listarVentas(token) {
  const res = await fetch(BASE_URL, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener ventas");
  return res.json();
}

export async function obtenerVenta(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Venta no encontrada");
  return res.json();
}

export async function crearVenta(venta, token) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(venta),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "Error al crear venta");
  }
  return res.json();
}

export async function anularVenta(id, token) {
  const res = await fetch(`${BASE_URL}/${id}/anular`, {
    method: "PATCH",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al anular venta");
}