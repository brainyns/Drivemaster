import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/pagos-marketplace`;

// Links de pago de Wompi. El frontend solo pide el link y redirige: la
// confirmación la hace el backend contra la API de Wompi.

async function post(token, url, msgDefault) {
  const res = await apiFetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    let msg = msgDefault;
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return res.json();
}

/**
 * Pide el link. Con redirección (servidor desplegado) lleva al cliente a Wompi y
 * devuelve null. En desarrollo local el backend omite redirect-url (Wompi da 403
 * con localhost): devuelve los datos para mostrar el aviso y verificar a mano.
 */
async function iniciar(token, url) {
  const data = await post(token, url, "No se pudo iniciar el pago");
  if (!data?.checkoutUrl) throw new Error("No se pudo iniciar el pago");
  if (!data.verificacionManual) {
    window.location.href = data.checkoutUrl;
    return null;
  }
  return data;
}

export function pagarDiagnostico(token, ordenId) {
  return iniciar(token, `${BASE}/diagnostico/${ordenId}`);
}

export function pagarCotizacion(token, ordenId) {
  return iniciar(token, `${BASE}/cotizacion/${ordenId}`);
}

/** Solo desarrollo (PAGO_SIMULADO_ENABLED en el backend). */
export function simularPago(token, referencia) {
  return post(token, `${BASE}/simular/${encodeURIComponent(referencia)}`, "No se pudo simular el pago");
}
