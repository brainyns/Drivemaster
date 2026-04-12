const BASE_URL = "http://localhost:8080/movimientos";

export async function listarMovimientos({ productoId, tipo, fechaInicio, fechaFin } = {}) {
  const params = new URLSearchParams();
  if (productoId)  params.append("productoId", productoId);
  if (tipo)        params.append("tipo", tipo);
  if (fechaInicio) params.append("fechaInicio", fechaInicio);
  if (fechaFin)    params.append("fechaFin", fechaFin);

  const res = await fetch(`${BASE_URL}?${params}`);
  if (!res.ok) throw new Error("Error al obtener movimientos");
  return res.json();
}

export async function registrarMovimiento({ productoId, tipo, cantidad, motivo }) {
  const params = new URLSearchParams({ productoId, tipo, cantidad, motivo });
  const res = await fetch(`${BASE_URL}/registrar?${params}`, { method: "POST" });
  if (!res.ok) throw new Error("Error al registrar movimiento");
  return res.json();
}