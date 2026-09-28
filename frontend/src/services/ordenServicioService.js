const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/ordenes-servicio`;

function headers(token) {
  const h = { "Content-Type": "application/json" };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

async function handle(res, msgDefault) {
  if (!res.ok) {
    let msg = msgDefault;
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

export async function cotizarOrden(token, { vehiculoId, productoId, tallerId }) {
  const res = await fetch(BASE, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ vehiculoId, productoId, tallerId }),
  });
  return handle(res, "Error al generar la cotización");
}

export async function pagarOrden(token, id, metodo = "EFECTIVO", referencia = null) {
  const res = await fetch(`${BASE}/${id}/pagar`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ metodo, referencia }),
  });
  return handle(res, "Error al procesar el pago");
}

/** Programa la fecha y hora de ingreso al taller. El taller debe enviar la orden. */
export async function programarCita(token, id, fechaProgramada) {
  const res = await fetch(`${BASE}/${id}/programar`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ fechaProgramada }),
  });
  return handle(res, "Error al programar la cita");
}

export async function cancelarOrden(token, id) {
  const res = await fetch(`${BASE}/${id}/cancelar`, { method: "PATCH", headers: headers(token) });
  return handle(res, "Error al cancelar la orden");
}

export async function misOrdenes(token) {
  const res = await fetch(`${BASE}/mis-ordenes`, { headers: headers(token) });
  return handle(res, "Error al listar tus órdenes");
}


export async function ordenesDelTaller(token) {
  const res = await fetch(`${BASE}/taller`, { headers: headers(token) });
  return handle(res, "Error al listar las órdenes del taller");
}

export async function ordenesPorTallerAdmin(token, tallerId) {
  const res = await fetch(`${BASE}/taller/${tallerId}`, { headers: headers(token) });
  return handle(res, "Error al listar las órdenes del taller");
}

export async function iniciarServicio(token, id) {
  const res = await fetch(`${BASE}/${id}/iniciar`, { method: "PATCH", headers: headers(token) });
  return handle(res, "Error al iniciar el servicio");
}

export async function finalizarServicio(token, id, observaciones) {
  const res = await fetch(`${BASE}/${id}/finalizar`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ observaciones }),
  });
  return handle(res, "Error al finalizar el servicio");
}