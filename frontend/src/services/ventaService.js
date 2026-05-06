const BASE = "http://localhost:8080/api";

const headers = (token) => ({
  "Content-Type": "application/json",
  ...(token && { Authorization: `Bearer ${token}` }),
});

// ── Ventas ───────────────────────────────────────────
export async function listarVentas(token) {
  const res = await fetch(`${BASE}/ventas`, { headers: headers(token) });
  if (!res.ok) throw new Error("Error al listar ventas");
  return res.json();
}

export async function obtenerVenta(id, token) {
  const res = await fetch(`${BASE}/ventas/${id}`, { headers: headers(token) });
  if (!res.ok) throw new Error("Venta no encontrada");
  return res.json();
}

export async function crearVenta(data, token) {
  const res = await fetch(`${BASE}/ventas/guardar`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "Error al crear venta");
  }
  return res.json();
}

export async function anularVenta(id, token) {
  const res = await fetch(`${BASE}/ventas/${id}/anular`, {
    method: "PATCH",
    headers: headers(token),
  });
  if (!res.ok) throw new Error("Error al anular venta");
}

// ── Config (MySQL) ───────────────────────────────────
export async function listarMetodosPago(token) {
  const res = await fetch(`${BASE}/config/metodos-pago`, {
    headers: headers(token),
  });
  if (!res.ok) throw new Error("Error al cargar métodos de pago");
  return res.json();
}

export async function obtenerParametro(clave, token) {
  const res = await fetch(`${BASE}/config/parametros/${clave}`, {
    headers: headers(token),
  });
  if (!res.ok) throw new Error(`Parámetro ${clave} no encontrado`);
  return res.json();
}