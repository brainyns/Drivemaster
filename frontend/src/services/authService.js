import { apiFetch } from "./http";
import { fetchConLimite } from "./conexion";

const API_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

const TOKEN_KEY = "auth_token";
const REFRESH_KEY = "auth_refresh";
const USER_KEY = "auth_user";

function handleResponse(response) {
  return response.text().then(text => {
    if (!response.ok) {
      let error = response.statusText || "Error de red";
      try {
        const data = JSON.parse(text);
        error = data.error || data.message || error;
      } catch {
        // ignore parse error, use default error message
      }
      throw new Error(error);
    }
    return text ? JSON.parse(text) : null;
  });
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUser() {
  const user = localStorage.getItem(USER_KEY);
  return user ? JSON.parse(user) : null;
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY);
}

export function setSession(token, user, refresh) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

/** Momento (ms) en que vence un JWT, leído de su claim "exp". null si no se puede leer. */
export function getTokenExpiracion(token) {
  if (!token) return null;
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const { exp } = JSON.parse(atob(payload));
    return typeof exp === "number" ? exp * 1000 : null;
  } catch {
    return null;
  }
}

function guardarSesion(data) {
  if (data && data.token) {
    setSession(data.token, {
      id: data.id,
      nombre: data.nombre,
      correo: data.correo,
      rol: data.rol,
      proveedor: data.proveedor,
      datosCompletos: data.datosCompletos
    }, data.refreshToken);
  }
}

export async function login(correo, password) {
  const res = await fetchConLimite(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ correo, password }),
  });
  const data = await handleResponse(res);
  guardarSesion(data);
  return data;
}

export async function loginInterno(correo, password) {
  const res = await fetchConLimite(`${API_BASE}/auth/login-interno`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ correo, password }),
  });
  const data = await handleResponse(res);
  guardarSesion(data);
  return data;
}

export async function googleLogin(idToken) {
  const res = await fetchConLimite(`${API_BASE}/auth/google`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken }),
  });
  const data = await handleResponse(res);
  guardarSesion(data);
  return data;
}

export async function register(data) {
  const res = await fetchConLimite(`${API_BASE}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  const response = await handleResponse(res);
  guardarSesion(response);
  return response;
}

/**
 * Renueva la sesión con el refresh token. Las sesiones abiertas antes de que
 * existiera el refresh token usan el token de acceso, si todavía está vigente.
 * Emite "auth:renovada" con el nuevo token para que App actualice su estado.
 */
export async function refreshToken() {
  const token = getToken();
  const refresh = getRefreshToken();
  if (!token && !refresh) {
    throw new Error("No hay sesión");
  }

  const res = await fetchConLimite(`${API_BASE}/auth/refresh`, {
    method: "POST",
    headers: refresh
      ? { "Content-Type": "application/json" }
      : { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: refresh ? JSON.stringify({ refreshToken: refresh }) : undefined,
  });

  const data = await handleResponse(res);
  if (data && data.token) {
    const user = getUser();
    setSession(data.token, {
      ...user,
      datosCompletos: data.datosCompletos
    }, data.refreshToken);
    window.dispatchEvent(new CustomEvent("auth:renovada", { detail: data.token }));
  }
  return data;
}

export async function logout() {
  const token = getToken();
  try {
    await fetchConLimite(`${API_BASE}/auth/logout`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      }
    });
  } catch {
    // ignore logout error
  }

  clearSession();
}

export async function listarUsuarios() {
  const res = await apiFetch(`${API_BASE}/usuarios`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`
    },
  });
  return handleResponse(res);
}

export async function crearUsuario(data) {
  const res = await apiFetch(`${API_BASE}/usuarios`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function eliminarUsuario(id) {
  const res = await apiFetch(`${API_BASE}/usuarios/${id}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`
    },
  });
  return handleResponse(res);
}

export async function actualizarMiNombre(nombre) {
  const res = await apiFetch(`${API_BASE}/clientes/mi-perfil/nombre`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify({ nombre }),
  });
  const data = await handleResponse(res);
  const user = getUser();
  if (user) {
    user.nombre = data.nombre;
    setSession(getToken(), user);
  }
  return data;
}

export function hasPermission(permission) {
  const user = getUser();
  const rolePermissions = {
    CLIENTE: ["PRODUCTOS_READ"],
    SUPERADMIN: ["USUARIOS_READ", "USUARIOS_WRITE", "USUARIOS_DELETE", "PRODUCTOS_READ", "PRODUCTOS_WRITE", "PRODUCTOS_DELETE", "CLIENTES_READ", "CLIENTES_WRITE", "CLIENTES_DELETE", "VENTAS_READ", "VENTAS_WRITE", "VENTAS_DELETE", "COMPRAS_READ", "COMPRAS_WRITE", "COMPRAS_DELETE", "PROVEEDORES_READ", "PROVEEDORES_WRITE", "PROVEEDORES_DELETE", "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE", "AJUSTES_READ", "AJUSTES_WRITE"],
    ADMIN: ["USUARIOS_READ", "USUARIOS_WRITE", "PRODUCTOS_READ", "PRODUCTOS_WRITE", "CLIENTES_READ", "CLIENTES_WRITE", "VENTAS_READ", "VENTAS_WRITE", "COMPRAS_READ", "COMPRAS_WRITE", "PROVEEDORES_READ", "PROVEEDORES_WRITE", "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE", "AJUSTES_READ", "AJUSTES_WRITE"],
    VENDEDOR: ["PRODUCTOS_READ", "CLIENTES_READ", "CLIENTES_WRITE", "VENTAS_READ", "VENTAS_WRITE"]
  };

  if (!user || !user.rol) return false;
  return rolePermissions[user.rol]?.includes(permission) || false;
}
