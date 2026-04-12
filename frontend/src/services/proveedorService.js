const BASE_URL = "http://localhost:8080/proveedores";

export async function listarProveedores() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener proveedores");
  return res.json();
}

export async function obtenerProveedor(id) {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Proveedor no encontrado");
  return res.json();
}

export async function crearProveedor(proveedor) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(proveedor),
  });
  if (!res.ok) throw new Error("Error al crear proveedor");
  return res.json();
}

export async function actualizarProveedor(id, proveedor) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(proveedor),
  });
  if (!res.ok) throw new Error("Error al actualizar proveedor");
  return res.json();
}

export async function eliminarProveedor(id) {
  const res = await fetch(`${BASE_URL}/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("Error al eliminar proveedor");
}