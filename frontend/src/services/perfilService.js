const API_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

function authHeaders(token) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

export async function getPerfil(token) {
  const res = await fetch(`${API_BASE}/perfil`, { headers: authHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener perfil");
  return res.json();
}

export async function cambiarPassword(token, passwordActual, nuevaPassword) {
  const res = await fetch(`${API_BASE}/auth/change-password`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ passwordActual, nuevaPassword }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "Error al cambiar contraseña");
  return data;
}
