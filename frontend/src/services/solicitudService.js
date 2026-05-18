const BASE = "http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/solicitudes";

function headers(token) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

export async function crearSolicitud(token, productos, metodoPago) {
  const res = await fetch(BASE, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ productos, metodoPago }),
  });
  if (!res.ok) {
    let msg = "Error al crear solicitud";
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch {}
    throw new Error(msg);
  }
  return res.json();
}

export async function listarSolicitudes(token) {
  const res = await fetch(BASE, { headers: headers(token) });
  if (!res.ok) throw new Error("Error al listar solicitudes");
  return res.json();
}

export async function obtenerSolicitud(token, id) {
  const res = await fetch(`${BASE}/${id}`, { headers: headers(token) });
  if (!res.ok) throw new Error("Solicitud no encontrada");
  return res.json();
}

export async function aprobarSolicitud(token, id) {
  const res = await fetch(`${BASE}/${id}/aprobar`, { method: "PATCH", headers: headers(token) });
  if (!res.ok) throw new Error("Error al aprobar solicitud");
  return res.json();
}

export async function comprarInmediata(token, productos, metodoPago) {
  const res = await fetch("http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/ventas/compra-inmediata", {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ productos, metodoPago }),
  });
  if (!res.ok) {
    let msg = "Error al procesar compra";
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch {}
    throw new Error(msg);
  }
  return res.json();
}

export async function rechazarSolicitud(token, id, motivo) {
  const res = await fetch(`${BASE}/${id}/rechazar`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ motivo }),
  });
  if (!res.ok) throw new Error("Error al rechazar solicitud");
  return res.json();
}
