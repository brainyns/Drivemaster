const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/vehiculos`;

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

export async function crearVehiculo(token, vehiculo) {
  const res = await fetch(BASE, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(vehiculo),
  });
  return handle(res, "Error al crear el vehículo");
}

export async function misVehiculos(token) {
  const res = await fetch(`${BASE}/mis-vehiculos`, { headers: headers(token) });
  return handle(res, "Error al listar los vehículos");
}

export async function obtenerVehiculo(token, id) {
  const res = await fetch(`${BASE}/${id}`, { headers: headers(token) });
  return handle(res, "Vehículo no encontrado");
}

export async function repuestosCompatibles(token, id) {
  const res = await fetch(`${BASE}/${id}/repuestos-compatibles`, { headers: headers(token) });
  return handle(res, "Error al buscar repuestos compatibles");
}

export async function historialVehiculo(token, id) {
  const res = await fetch(`${BASE}/${id}/historial`, { headers: headers(token) });
  return handle(res, "Error al obtener el historial del vehículo");
}

export async function subirImagenVehiculo(token, id, archivo) {
  const formData = new FormData();
  formData.append("archivo", archivo);
  const res = await fetch(`${BASE}/${id}/imagen`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  return handle(res, "Error al subir la imagen del vehículo");
}

/** Ajusta el intervalo de mantenimiento y/o activa o desactiva los recordatorios. */
export async function configurarMantenimiento(token, id, config) {
  const res = await fetch(`${BASE}/${id}/mantenimiento`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify(config),
  });
  return handle(res, "Error al guardar la configuración de mantenimiento");
}

/** Dispara el barrido de recordatorios manualmente. */
export async function enviarRecordatorios(token) {
  const res = await fetch(`${BASE}/mantenimiento/enviar-recordatorios`, {
    method: "POST",
    headers: headers(token),
  });
  return handle(res, "Error al enviar los recordatorios");
}