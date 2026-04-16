const BASE_URL = "http://localhost:8080/productos";

const buildHeaders = (token, contentType = "application/json") => {
  const headers = {};
  if (contentType) headers["Content-Type"] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

// Listar todos los productos
export async function listarProductos(token) {
  const res = await fetch(BASE_URL, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener productos");
  return res.json();
}

// Obtener producto por ID
export async function obtenerProducto(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Producto no encontrado");
  return res.json();
}

// Crear producto
export async function crearProducto(producto, token) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(producto),
  });
  if (!res.ok) throw new Error("Error al crear producto");
  return res.json();
}

// Actualizar producto
export async function actualizarProducto(id, producto, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: buildHeaders(token),
    body: JSON.stringify(producto),
  });
  if (!res.ok) throw new Error("Error al actualizar producto");
  return res.json();
}

// Eliminar producto
export async function eliminarProducto(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al eliminar producto");
}

// Actualizar stock
export async function actualizarStock(id, nuevoStock, token) {
  const res = await fetch(`${BASE_URL}/${id}/stock`, {
    method: "PATCH",
    headers: buildHeaders(token),
    body: JSON.stringify({ stock: nuevoStock }),
  });
  if (!res.ok) throw new Error("Error al actualizar stock");
  return res.json();
}