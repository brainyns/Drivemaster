const BASE_URL = "http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/compras";

const buildHeaders = (token, contentType = "application/json") => {
  const headers = {};
  if (contentType) headers["Content-Type"] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function listarCompras(token) {
  const res = await fetch(BASE_URL, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener compras");
  return res.json();
}

export async function obtenerCompra(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Compra no encontrada");
  return res.json();
}

export async function crearCompra(compra, token) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(compra),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "Error al crear compra");
  }
  return res.json();
}

export async function descargarPdfCompra(id, token) {
  const res = await fetch(`${BASE_URL}/${id}/pdf`, {
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