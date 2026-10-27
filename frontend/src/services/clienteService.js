import { apiFetch } from "./http";
import { errorDeRespuesta } from "../utils/errores";
const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/clientes`;

const buildHeaders = (token) => {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

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
  // El mensaje del servidor dice qué falta (p. ej. "Escoge tu ciudad de la lista.")
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudieron guardar tus datos");
  return res.json();
}
