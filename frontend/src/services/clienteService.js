const BASE_URL = "http://localhost:8080/clientes";

const buildHeaders = (token) => {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function listarClientes(token) {
  const res = await fetch(BASE_URL, {
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al obtener clientes");
  return res.json();
}

export async function obtenerCliente(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Cliente no encontrado");
  return res.json();
}

export async function crearCliente(cliente, token) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(cliente),
  });
  if (!res.ok) throw new Error("Error al crear cliente");
  return res.json();
}

export async function actualizarCliente(id, cliente, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: buildHeaders(token),
    body: JSON.stringify(cliente),
  });
  if (!res.ok) throw new Error("Error al actualizar cliente");
  return res.json();
}

export async function eliminarCliente(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al eliminar cliente");
}