const BASE_URL = "http://localhost:8080/compras";

export async function listarCompras() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener compras");
  return res.json();
}

export async function obtenerCompra(id) {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Compra no encontrada");
  return res.json();
}

export async function crearCompra(compra) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(compra),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "Error al crear compra");
  }
  return res.json();
}