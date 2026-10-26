import { getToken, refreshToken, clearSession } from "./authService";
import { fetchConLimite } from "./conexion";

// Códigos con los que el backend marca un 401 por sesión (no por reglas de negocio,
// como una contraseña actual incorrecta, que también responde 401).
const CODIGOS_SESION = ["SESSION_EXPIRED", "UNAUTHENTICATED"];

let renovacionEnCurso = null;

function conToken(options = {}) {
  const headers = new Headers(options.headers || {});
  // Siempre el token vigente de localStorage: los componentes que reciben el token
  // por props pueden tener uno viejo tras una renovación.
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  else headers.delete("Authorization");
  return { ...options, headers };
}

async function esErrorDeSesion(res) {
  if (res.status !== 401) return false;
  try {
    const data = await res.clone().json();
    return CODIGOS_SESION.includes(data?.code);
  } catch {
    return false;
  }
}

/**
 * fetch con autenticación. Si el backend responde 401 por sesión vencida, renueva
 * el token una sola vez (compartida entre peticiones simultáneas) y reintenta.
 * Si no se puede renovar, cierra la sesión y emite "auth:expirada". Si el servidor no responde en 20 s o no hay red,
 * lanza ErrorConexion con un mensaje claro (lo muestran las Alertas con "Reintentar" de cada pantalla).
 */
export async function apiFetch(url, options = {}) {
  const res = await fetchConLimite(url, conToken(options));
  if (!getToken() || !(await esErrorDeSesion(res))) return res;

  try {
    renovacionEnCurso ??= refreshToken().finally(() => { renovacionEnCurso = null; });
    await renovacionEnCurso;
  } catch {
    clearSession();
    window.dispatchEvent(new Event("auth:expirada"));
    return res;
  }

  return fetchConLimite(url, conToken(options));
}
