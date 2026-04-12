const BASE_URL = "http://localhost:8080/ventas";

export async function listarVentas() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener ventas");
  return res.json();
}

export async function obtenerVenta(id) {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Venta no encontrada");
  return res.json();
}

export async function crearVenta(venta) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(venta),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "Error al crear venta");
  }
  return res.json();
}

export async function anularVenta(id) {
  const res = await fetch(`${BASE_URL}/${id}/anular`, { method: "PATCH" });
  if (!res.ok) throw new Error("Error al anular venta");
}