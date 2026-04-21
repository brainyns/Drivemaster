const API_BASE = "http://localhost:8080/api";

const TOKEN_KEY = "auth_token";
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

export function setSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export async function login(correo, password) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ correo, password }),
  });
  const data = await handleResponse(res);
  if (data && data.token) {
    setSession(data.token, {
      id: data.id,
      nombre: data.nombre,
      correo: data.correo,
      rol: data.rol
    });
  }
  return data;
}

export async function register(data) {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  const response = await handleResponse(res);
  if (response && response.token) {
    setSession(response.token, {
      id: response.id,
      nombre: response.nombre,
      correo: response.correo,
      rol: response.rol
    });
  }
  return response;
}

export async function refreshToken() {
  const token = getToken();
  if (!token) {
    throw new Error("No hay sesión");
  }

  const res = await fetch(`${API_BASE}/auth/refresh`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`
    }
  });

  const data = await handleResponse(res);
  if (data && data.token) {
    const user = getUser();
    setSession(data.token, user);
  }
  return data;
}

export async function logout() {
  const token = getToken();
  try {
    await fetch(`${API_BASE}/auth/logout`, {
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
  const res = await fetch(`${API_BASE}/usuarios`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`
    },
  });
  return handleResponse(res);
}

export async function crearUsuario(data) {
  const res = await fetch(`${API_BASE}/usuarios`, {
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
  const res = await fetch(`${API_BASE}/usuarios/${id}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`
    },
  });
  return handleResponse(res);
}

export function hasPermission(permission) {
  const user = getUser();
  const rolePermissions = {
    SUPERADMIN: ["USUARIOS_READ", "USUARIOS_WRITE", "USUARIOS_DELETE", "PRODUCTOS_READ", "PRODUCTOS_WRITE", "PRODUCTOS_DELETE", "CLIENTES_READ", "CLIENTES_WRITE", "CLIENTES_DELETE", "VENTAS_READ", "VENTAS_WRITE", "VENTAS_DELETE", "COMPRAS_READ", "COMPRAS_WRITE", "COMPRAS_DELETE", "PROVEEDORES_READ", "PROVEEDORES_WRITE", "PROVEEDORES_DELETE", "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE", "AJUSTES_READ", "AJUSTES_WRITE"],
    ADMIN: ["USUARIOS_READ", "USUARIOS_WRITE", "PRODUCTOS_READ", "PRODUCTOS_WRITE", "CLIENTES_READ", "CLIENTES_WRITE", "VENTAS_READ", "VENTAS_WRITE", "COMPRAS_READ", "COMPRAS_WRITE", "PROVEEDORES_READ", "PROVEEDORES_WRITE", "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE", "AJUSTES_READ", "AJUSTES_WRITE"],
    VENDEDOR: ["PRODUCTOS_READ", "CLIENTES_READ", "CLIENTES_WRITE", "VENTAS_READ", "VENTAS_WRITE"]
  };

  if (!user || !user.rol) return false;
  return rolePermissions[user.rol]?.includes(permission) || false;
}
