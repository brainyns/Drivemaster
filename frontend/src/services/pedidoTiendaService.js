import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/pedidos-tienda`;

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

function enviar(token, metodo, url, body, msg) {
  return apiFetch(url, { method: metodo, headers: headers(token), body: JSON.stringify(body || {}) })
    .then((res) => handle(res, msg));
}

// ─── Tienda ────────────────────────────────────────────────────────────────

export async function misPedidosTienda(token) {
  const res = await apiFetch(`${BASE}/mios`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar los pedidos");
}

/** Solo encargos: la tienda ya está consiguiendo el repuesto (PENDIENTE → EN_PREPARACION). */
export function prepararPedido(token, id) {
  return enviar(token, "PATCH", `${BASE}/${id}/preparar`, null, "No se pudo iniciar la preparación");
}

export function despacharPedido(token, id, nota) {
  return enviar(token, "PATCH", `${BASE}/${id}/despachar`, { nota }, "No se pudo marcar el despacho");
}

/** tipoMotivo: SIN_REPUESTO | OTRO (detalle obligatorio en OTRO) */
export function noPuedoDespachar(token, id, tipoMotivo, detalle) {
  return enviar(token, "PATCH", `${BASE}/${id}/no-despachar`, { tipoMotivo, detalle }, "No se pudo registrar");
}

// ─── Por orden (cliente, taller, ADMIN) ────────────────────────────────────

export async function pedidosDeOrden(token, ordenId) {
  const res = await apiFetch(`${BASE}/orden/${ordenId}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar los pedidos");
}

export function confirmarRepuestosRecibidos(token, ordenId) {
  return enviar(token, "POST", `${BASE}/orden/${ordenId}/recibidos`, null, "No se pudo confirmar la recepción");
}

// ─── Compras del catálogo (retiro con código) ──────────────────────────────

/** Retiro: el pedido está listo y el cliente ya puede pasar por él. */
export function marcarListoParaRetirar(token, id) {
  return enviar(token, "PATCH", `${BASE}/${id}/listo-retiro`, null, "No se pudo marcar como listo para retirar");
}

/** La tienda valida el código que muestra el cliente (un solo uso; 5 errores bloquean el pedido 15 minutos). */
export function validarCodigoRetiro(token, id, codigo) {
  return enviar(token, "POST", `${BASE}/${id}/retiro/validar`, { codigo }, "No se pudo validar el código");
}

// ─── ADMIN: problemas de envío ─────────────────────────────────────────────

export async function problemasDeEnvio(token) {
  const res = await apiFetch(`${BASE}/problemas`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar los problemas de envío");
}

/** resolucion: "CONFIRMAR_ENTREGA" | "REEMBOLSAR"; el motivo escrito es obligatorio. */
export function resolverProblemaEnvio(token, id, resolucion, motivo) {
  return enviar(token, "POST", `${BASE}/${id}/problema/resolver`, { resolucion, motivo }, "No se pudo resolver el problema");
}
