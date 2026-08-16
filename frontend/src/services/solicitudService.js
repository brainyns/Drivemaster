const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/solicitudes`;
function headers(token) {
  const h = {
    "Content-Type": "application/json",
  };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

// El redirect pasa por el backend (confirmar-redirect) para que la verificación
// del pago y el envío de la factura se hagan del lado del servidor, sin depender
// de que la página /pago-resultado del frontend cargue correctamente.
export function getWompiRedirectUrl() {
  const configurada = import.meta.env.VITE_WOMPI_REDIRECT_URL;
  if (configurada) return configurada;
  const api = import.meta.env.VITE_API_URL || "http://localhost:8080";
  return `${api}/api/pagos/confirmar-redirect`;
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

export async function marcarEnCamino(token, id) {
  const res = await fetch(`${BASE}/${id}/en-camino`, { method: "PATCH", headers: headers(token) });
  if (!res.ok) throw new Error("Error al marcar solicitud en camino");
  return res.json();
}

export async function marcarEntregado(token, id) {
  const res = await fetch(`${BASE}/${id}/entregado`, { method: "PATCH", headers: headers(token) });
  if (!res.ok) throw new Error("Error al marcar solicitud entregada");
  return res.json();
}

export async function comprarInmediata(token, productos, metodoPago) {
  const res = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/ventas/compra-inmediata`, {
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

const PAGOS_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/pagos`;

export async function crearPago(token, solicitudId, redirectUrl) {
  const res = await fetch(`${PAGOS_BASE}/crear`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ solicitudId, redirectUrl }),
  });
  if (!res.ok) {
    let msg = "Error al crear pago";
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch {}
    throw new Error(msg);
  }
  return res.json();
}

export async function crearPagoVenta(token, ventaId, redirectUrl) {
  const res = await fetch(`${PAGOS_BASE}/crear`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ ventaId, redirectUrl }),
  });
  if (!res.ok) {
    let msg = "Error al crear pago";
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch {}
    throw new Error(msg);
  }
  return res.json();
}

export async function verificarPago(token, transactionId) {
  const res = await fetch(`${PAGOS_BASE}/verificar/${transactionId}`, {
    headers: headers(token),
  });
  if (!res.ok) throw new Error("Error al verificar pago");
  return res.json();
}

export async function confirmarPago(token, solicitudId, transactionId, referencia) {
  const res = await fetch(`${PAGOS_BASE}/confirmar`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ solicitudId, transactionId, referencia }),
  });
  if (!res.ok) throw new Error("Error al confirmar pago");
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
