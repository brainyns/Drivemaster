const API_BASE = "http://localhost:8080/api";

async function handleResponse(response) {
  const content = await response.text();
  if (!response.ok) {
    const error = content || response.statusText || "Error de red";
    throw new Error(error);
  }
  return content ? JSON.parse(content) : null;
}

export async function login(correo, password) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ correo, password }),
  });
  return handleResponse(res);
}

export async function register(data) {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function listarUsuarios(token) {
  const res = await fetch(`${API_BASE}/users`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
  });
  return handleResponse(res);
}

export async function crearUsuario(data, token) {
  const res = await fetch(`${API_BASE}/users`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  });
  return handleResponse(res);
}
