import { apiFetch } from "./http";

const BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/plataforma`;

function headers(token) {
  const h = { "Content-Type": "application/json" };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

/** Arma ?a=1&b=2 solo con los filtros que tienen valor. */
function consulta(filtros = {}) {
  const p = new URLSearchParams();
  Object.entries(filtros).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== "") p.set(k, String(v).trim());
  });
  const s = p.toString();
  return s ? `?${s}` : "";
}

async function leer(res, fallo) {
  if (res.status === 403) throw new Error("No tienes permiso para ver esta sección.");
  if (!res.ok) throw new Error(fallo);
  return res.json();
}

/** Cifras del panel de la plataforma (solo SUPERADMIN). */
export async function dashboardPlataforma(token) {
  return leer(await apiFetch(`${BASE}/dashboard`, { headers: headers(token) }), "No se pudo cargar el panel.");
}

/** Lo que el ADMIN tiene que atender (negocios por aprobar, problemas de envío y despachos atrasados). */
export async function operacionPlataforma(token) {
  return leer(await apiFetch(`${BASE}/operacion`, { headers: headers(token) }), "No se pudo cargar tu resumen.");
}

/** Monitor de pedidos y envíos (ADMIN y SUPERADMIN). Filtros: estado, tiendaId, entrega, atrasados, pagina, tamano. */
export async function pedidosPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/pedidos${consulta(filtros)}`, { headers: headers(token) }),
    "No se pudieron cargar los pedidos.");
}

// ─── Reportes de la plataforma (solo SUPERADMIN) ───────────────────────────

export async function reporteVentasPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/reportes/ventas${consulta(filtros)}`, { headers: headers(token) }), "No se pudo cargar el reporte de ventas.");
}

export async function reporteProductosPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/reportes/productos${consulta(filtros)}`, { headers: headers(token) }), "No se pudo cargar el reporte de productos.");
}

export async function reporteClientesPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/reportes/clientes${consulta(filtros)}`, { headers: headers(token) }), "No se pudo cargar el reporte de clientes.");
}

export async function reporteServiciosPlataforma(token) {
  return leer(await apiFetch(`${BASE}/reportes/servicios`, { headers: headers(token) }), "No se pudo cargar el reporte de servicios.");
}

export async function reporteComisionPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/reportes/comision${consulta(filtros)}`, { headers: headers(token) }), "No se pudo cargar el reporte de comisión.");
}

/** Usuarios del sistema (correo enmascarado). Filtros: q, rol, estado, pagina, tamano. */
export async function usuariosPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/usuarios${consulta(filtros)}`, { headers: headers(token) }), "No se pudieron cargar los usuarios.");
}

/** Tiendas y talleres en una lista (solo SUPERADMIN). Filtros: tipo, estado, plan (código o SIN_PLAN), q, pagina, tamano. */
export async function negociosPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/negocios${consulta(filtros)}`, { headers: headers(token) }), "No se pudieron cargar los negocios.");
}

/** Negocios por revisar, con lo mínimo para decidir (ADMIN y SUPERADMIN). */
export async function negociosPendientes(token) {
  return leer(await apiFetch(`${BASE}/negocios/pendientes`, { headers: headers(token) }), "No se pudieron cargar las aprobaciones.");
}

/** Catálogo global de solo lectura. Filtros: q, tiendaId, nuevosDias (7|30), estado, pagina, tamano. */
export async function productosPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/productos${consulta(filtros)}`, { headers: headers(token) }),
    "No se pudieron cargar los productos.");
}

/** Clientes registrados (solo lectura). Filtros: q, ciudad, estado, pagina, tamano. */
export async function clientesPlataforma(token, filtros) {
  return leer(await apiFetch(`${BASE}/clientes${consulta(filtros)}`, { headers: headers(token) }),
    "No se pudieron cargar los clientes.");
}
