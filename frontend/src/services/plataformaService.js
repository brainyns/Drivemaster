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
