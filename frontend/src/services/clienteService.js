const BASE_URL = "http://localhost:8080/clientes";

export async function listarClientes() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener clientes");
  return res.json();
}

export async function obtenerCliente(id) {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Cliente no encontrado");
  return res.json();
}

export async function crearCliente(cliente) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cliente),
  });
  if (!res.ok) throw new Error("Error al crear cliente");
  return res.json();
}

export async function actualizarCliente(id, cliente) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cliente),
  });
  if (!res.ok) throw new Error("Error al actualizar cliente");
  return res.json();
}

export async function eliminarCliente(id) {
  const res = await fetch(`${BASE_URL}/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("Error al eliminar cliente");
}