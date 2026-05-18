const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/reportes`;

const buildHeaders = (token) => {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

// ── DASHBOARD GENERAL ──────────────────────────────────────────────────────
export async function getDashboard(token) {
  const res = await fetch(`${BASE_URL}/dashboard`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener dashboard");
  return res.json();
}

// ── CLIENTES ───────────────────────────────────────────────────────────────
export async function getInventarioClientes(token) {
  const res = await fetch(`${BASE_URL}/clientes/inventario`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener inventario de clientes");
  return res.json();
}

export async function getReportesClientes(token, periodo = "mes") {
  const res = await fetch(`${BASE_URL}/clientes/reportes?periodo=${periodo}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener reportes de clientes");
  return res.json();
}

export async function getHistorialCliente(clienteId, token) {
  const res = await fetch(`${BASE_URL}/clientes/${clienteId}/historial`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener historial del cliente");
  return res.json();
}

// ── PRODUCTOS ──────────────────────────────────────────────────────────────
export async function getInventarioProductos(token) {
  const res = await fetch(`${BASE_URL}/productos/inventario`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener inventario de productos");
  return res.json();
}

export async function getReportesProductos(token, periodo = "mes") {
  const res = await fetch(`${BASE_URL}/productos/reportes?periodo=${periodo}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener reportes de productos");
  return res.json();
}