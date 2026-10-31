import { apiFetch } from "./http";
import { consultaTabla } from "../components/ui/reglasTabla";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/liquidaciones`;

function headers(token) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

async function handle(res, msgDefault) {
  if (!res.ok) {
    let msg = msgDefault;
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.json();
}

export async function listarRepartos(token, estado) {
  const res = await apiFetch(`${BASE}/repartos${estado ? `?estado=${estado}` : ""}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar las liquidaciones");
}

/**
 * Liquidaciones paginadas en el servidor: { repartos: Pagina, pendientes, montoPendiente, resumen }. Parámetros: estado, desde,
 * hasta, page, size y sort ("monto,desc"). pendientes y montoPendiente cuentan toda la lista filtrada.
 */
export async function repartosTabla(token, parametros) {
  const res = await apiFetch(`${BASE}/repartos/tabla${consultaTabla(parametros)}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar las liquidaciones");
}

/** Modo de dispersión del backend: { modo: "SIMULADO" | "MANUAL" | "WOMPI", automatica, dineroReal } */
export async function obtenerModo(token) {
  const res = await apiFetch(`${BASE}/modo`, { headers: headers(token) });
  return handle(res, "No se pudo consultar el modo de liquidación");
}

// Wompi Payouts puede tardar más que el límite normal: cortar antes diría "sin conexión" con el pago en curso
const LIMITE_LIQUIDAR_MS = 60000;

/** En modo MANUAL el número de transferencia es obligatorio; en SIMULADO el backend lo ignora. */
export async function liquidarReparto(token, id, { numeroTransferencia, nota } = {}) {
  const res = await apiFetch(`${BASE}/repartos/${id}/liquidar`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ numeroTransferencia, nota }),
  }, LIMITE_LIQUIDAR_MS);
  return handle(res, "No se pudo liquidar");
}

/** Salida manual de una línea PROCESANDO sin evento de Wompi: resultado LIQUIDADO (sí salió) o FALLIDO (no salió). */
export async function resolverReparto(token, id, { resultado, motivo, referencia }) {
  const res = await apiFetch(`${BASE}/repartos/${id}/resolver`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ resultado, motivo, referencia }),
  });
  return handle(res, "No se pudo resolver el pago");
}

export async function listarReembolsos(token, estado) {
  const res = await apiFetch(`${BASE}/reembolsos${estado ? `?estado=${estado}` : ""}`, { headers: headers(token) });
  return handle(res, "No se pudieron cargar los reembolsos");
}

/** El negocio ya devolvió lo que quedó a favor de DriveMaster en un reembolso. */
export async function marcarSaldoCobrado(token, id) {
  const res = await apiFetch(`${BASE}/reembolsos/${id}/saldo-cobrado`, { method: "PATCH", headers: headers(token) });
  return handle(res, "No se pudo marcar el saldo como cobrado");
}

export async function marcarReembolsado(token, id, nota) {
  const res = await apiFetch(`${BASE}/reembolsos/${id}/realizado`, {
    method: "PATCH", headers: headers(token), body: JSON.stringify({ nota }),
  });
  return handle(res, "No se pudo marcar como reembolsado");
}
