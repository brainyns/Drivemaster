import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/talleres`;

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

export async function talleresActivos(token, { ciudad, lat, lng } = {}) {
  const params = new URLSearchParams();
  if (ciudad) params.set("ciudad", ciudad);
  if (lat != null && lng != null) {
    params.set("lat", lat);
    params.set("lng", lng);
  }
  const qs = params.toString();
  const res = await apiFetch(`${BASE}/activos${qs ? `?${qs}` : ""}`, { headers: headers(token) });
  return handle(res, "Error al listar los talleres");
}

/** Todos los talleres, o solo los de un estado de revisión (PENDIENTE, APROBADO, RECHAZADO). */
export async function listarTalleres(token, estado) {
  const res = await apiFetch(`${BASE}${estado ? `?estado=${encodeURIComponent(estado)}` : ""}`, { headers: headers(token) });
  return handle(res, "Error al listar los talleres");
}

/** Registro público de un taller. Queda pendiente hasta que DriveMaster lo apruebe. */
export async function registrarTaller(datos) {
  const res = await fetch(`${BASE}/registro`, {
    method: "POST",
    headers: headers(null),
    body: JSON.stringify(datos),
  });
  return handle(res, "No se pudo registrar el taller");
}

export async function miTaller(token) {
  const res = await apiFetch(`${BASE}/mi-taller`, { headers: headers(token) });
  return handle(res, "Error al cargar los datos del taller");
}

// ─── Mecánicos del taller autenticado ───

export async function listarMecanicos(token) {
  const res = await apiFetch(`${BASE}/mi-taller/mecanicos`, { headers: headers(token) });
  return handle(res, "Error al cargar los mecánicos");
}

/** mecanico: { nombre, especialidad?, telefono? } */
export async function agregarMecanico(token, mecanico) {
  const res = await apiFetch(`${BASE}/mi-taller/mecanicos`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(mecanico),
  });
  return handle(res, "No se pudo agregar el mecánico");
}

export async function actualizarMecanico(token, id, mecanico) {
  const res = await apiFetch(`${BASE}/mi-taller/mecanicos/${id}`, {
    method: "PUT",
    headers: headers(token),
    body: JSON.stringify(mecanico),
  });
  return handle(res, "No se pudo actualizar el mecánico");
}

export async function cambiarEstadoMecanico(token, id, activo) {
  const res = await apiFetch(`${BASE}/mi-taller/mecanicos/${id}/activo`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ activo }),
  });
  return handle(res, "No se pudo cambiar el estado del mecánico");
}

/** Body: { aprobado, motivo?, planCodigo? } */
export async function resolverAprobacionTaller(token, id, decision) {
  const res = await apiFetch(`${BASE}/${id}/aprobacion`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify(decision),
  });
  return handle(res, "Error al registrar la decisión");
}

export async function crearTaller(token, taller) {
  const res = await apiFetch(BASE, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(taller),
  });
  return handle(res, "Error al crear el taller");
}

export async function actualizarTaller(token, id, taller) {
  const res = await apiFetch(`${BASE}/${id}`, {
    method: "PUT",
    headers: headers(token),
    body: JSON.stringify(taller),
  });
  return handle(res, "Error al actualizar el taller");
}

export async function activarDesactivarTaller(token, id, activo) {
  const res = await apiFetch(`${BASE}/${id}/activo`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ activo }),
  });
  return handle(res, "Error al cambiar el estado del taller");
}

/** El taller cambia su tarifa por hora y su horario. Solo viajan esos dos campos. */
/** Solo el teléfono del taller de la sesión (celular o fijo colombiano). Devuelve el taller actualizado. */
export async function guardarTelefonoTaller(token, telefono) {
  const res = await apiFetch(`${BASE}/mi-taller/telefono`, {
    method: "PUT",
    headers: headers(token),
    body: JSON.stringify({ telefono }),
  });
  return handle(res, "No se pudo guardar el teléfono");
}

/** Solo viajan los campos indicados (los undefined no se mandan y el backend los deja como estaban). */
export async function actualizarDatosTaller(token, { tarifaHora, horario, responsableIva }) {
  const res = await apiFetch(`${BASE}/mi-taller/datos`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ tarifaHora, horario, responsableIva }),
  });
  if (res.status === 403) throw new Error("Tu taller aún no está aprobado, así que todavía no puedes editar estos datos.");
  return handle(res, "No se pudieron guardar los cambios");
}

/** Talleres aprobados para elegir uno al pedir un diagnóstico; los que no tienen ubicación llegan con latitud null. */
export async function talleresMapa(token) {
  const res = await apiFetch(`${BASE}/mapa`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar los talleres del mapa");
}

/**
 * Los talleres más cercanos a un punto (Haversine en el backend) con la distancia y el tiempo en carro de OSRM
 * cuando el servicio de rutas responde (si no, duracionMin llega en null).
 */
export async function talleresCercanos(token, { latitud, longitud }, { limite = 3, signal } = {}) {
  const params = new URLSearchParams({ lat: String(latitud), lng: String(longitud), limite: String(limite) });
  const res = await apiFetch(`${BASE}/cercanos?${params}`, { headers: headers(token), signal });
  return handle(res, "No se pudieron calcular los talleres más cercanos");
}

/** El taller sube su logo (o una foto del local). Responde { logoUrl }. Mismo envío que la foto del vehículo. */
export async function subirLogoTaller(token, archivo) {
  const formData = new FormData();
  formData.append("archivo", archivo);
  const res = await apiFetch(`${BASE}/mi-taller/logo`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  return handle(res, "No se pudo subir el logo");
}

/** El taller ajusta el punto exacto de su local en el mapa. */
export async function actualizarUbicacionTaller(token, { latitud, longitud }) {
  const res = await apiFetch(`${BASE}/mi-taller/datos`, {
    method: "PATCH",
    headers: headers(token),
    body: JSON.stringify({ latitud, longitud }),
  });
  if (res.status === 403) throw new Error("Tu taller aún no está aprobado, así que todavía no puedes cambiar su ubicación.");
  return handle(res, "No se pudo guardar la ubicación");
}
