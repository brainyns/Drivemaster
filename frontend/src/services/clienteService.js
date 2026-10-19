import { apiFetch } from "./http";
const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/clientes`;

const buildHeaders = (token) => {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function listarClientes(token) {
  const res = await apiFetch(BASE_URL, {
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al obtener clientes");
  return res.json();
}

/**
 * Perfil del cliente autenticado (PerfilClienteResponse: dirección, ciudad, teléfono, documento...). Un cliente
 * nuevo recibe sus campos vacíos (200); el 404 solo llega si la cuenta de la sesión ya no existe. El error lleva
 * `estado` para que quien llama distinga ese caso de una falla pasajera.
 */
export async function obtenerMiPerfil(token) {
  const res = await apiFetch(`${BASE_URL}/mi-perfil`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) {
    const error = new Error(res.status === 404
      ? "No encontramos tu cuenta. Cierra sesión y vuelve a entrar."
      : "No se pudo cargar tu perfil");
    error.estado = res.status;
    throw error;
  }
  return res.json();
}

/** Guarda los datos de contacto del cliente autenticado (PUT /mi-perfil: identificación, teléfono, dirección, ciudad, referencia). */
export async function actualizarMiPerfil(token, datos) {
  const res = await apiFetch(`${BASE_URL}/mi-perfil`, {
    method: "PUT",
    headers: buildHeaders(token),
    body: JSON.stringify(datos),
  });
  if (!res.ok) throw new Error("No se pudieron guardar tus datos");
  return res.json();
}

export async function obtenerCliente(id, token) {
  const res = await apiFetch(`${BASE_URL}/${id}`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Cliente no encontrado");
  return res.json();
}

export async function crearCliente(cliente, token) {
  const res = await apiFetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(cliente),
  });
  if (!res.ok) throw new Error("Error al crear cliente");
  return res.json();
}

export async function actualizarCliente(id, cliente, token) {
  const res = await apiFetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: buildHeaders(token),
    body: JSON.stringify(cliente),
  });
  if (!res.ok) throw new Error("Error al actualizar cliente");
  return res.json();
}

export async function eliminarCliente(id, token) {
  const res = await apiFetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al eliminar cliente");
}

export async function exportarClientes(token, formato = "pdf") {
  const res = await apiFetch(`${BASE_URL}/exportar?formato=${formato}`, {
    headers: buildHeaders(token),
  });
  if (!res.ok) {
    let msg = "Error al exportar";
    try { const data = await res.json(); msg = data.message || data.error || msg; } catch { /* ignore */ }
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