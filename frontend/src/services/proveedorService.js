const BASE_URL = "http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/proveedores";

const buildHeaders = (token, contentType = "application/json") => {
  const headers = {};
  if (contentType) headers["Content-Type"] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function listarProveedores(token) {
  const res = await fetch(BASE_URL, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener proveedores");
  return res.json();
}

export async function obtenerProveedor(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Proveedor no encontrado");
  return res.json();
}

export async function crearProveedor(proveedor, token) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(proveedor),
  });
  if (!res.ok) throw new Error("Error al crear proveedor");
  return res.json();
}

export async function actualizarProveedor(id, proveedor, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: buildHeaders(token),
    body: JSON.stringify(proveedor),
  });
  if (!res.ok) throw new Error("Error al actualizar proveedor");
  return res.json();
}

export async function eliminarProveedor(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al eliminar proveedor");
}

export async function exportarProveedores(token) {
  const res = await fetch(`${BASE_URL}/exportar`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al exportar proveedores");
  const blob = await res.blob();
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement("a");
  a.href     = url;
  a.download = "Proveedores-DriveMaster.pdf";
  a.click();
  URL.revokeObjectURL(url);
}