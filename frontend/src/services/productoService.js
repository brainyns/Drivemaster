const BASE_URL = "http://localhost:8080/productos";

// Listar todos los productos
export async function listarProductos() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener productos");
  return res.json();
}

// Obtener producto por ID
export async function obtenerProducto(id) {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Producto no encontrado");
  return res.json();
}

// Crear producto
export async function crearProducto(producto) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(producto),
  });
  if (!res.ok) throw new Error("Error al crear producto");
  return res.json();
}

// Actualizar producto
export async function actualizarProducto(id, producto) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(producto),
  });
  if (!res.ok) throw new Error("Error al actualizar producto");
  return res.json();
}

// Eliminar producto
export async function eliminarProducto(id) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error("Error al eliminar producto");
}

// Actualizar stock
export async function actualizarStock(id, nuevoStock) {
  const res = await fetch(`${BASE_URL}/${id}/stock`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ stock: nuevoStock }),
  });
  if (!res.ok) throw new Error("Error al actualizar stock");
  return res.json();
}