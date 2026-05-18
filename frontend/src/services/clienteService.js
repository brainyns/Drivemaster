const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/clientes`;

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

export async function exportarClientes(token, formato = "pdf") {
  const res = await fetch(`${BASE_URL}/exportar?formato=${formato}`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) {
    let msg = "Error al exportar";
    try { const data = await res.json(); msg = data.message || data.error || msg; } catch {}
    throw new Error(msg);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `Clientes-DriveMaster.${formato === "pdf" ? "pdf" : "xlsx"}`;
  a.click();
  URL.revokeObjectURL(url);
}