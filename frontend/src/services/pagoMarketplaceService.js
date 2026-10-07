import { apiFetch } from "./http";
const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/pagos-marketplace`;

// Links de pago de Wompi. El frontend solo pide el link y redirige: la
// confirmación la hace el backend contra la API de Wompi.

async function crearLink(token, url) {
  const res = await apiFetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    let msg = "No se pudo iniciar el pago";
    try { const data = await res.json(); msg = data.error || data.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  const data = await res.json();
  if (!data?.checkoutUrl) throw new Error("No se pudo iniciar el pago");
  return data.checkoutUrl;
}

/** Pide el link y lleva al cliente al checkout de Wompi. */
export async function pagarDiagnostico(token, ordenId) {
  window.location.href = await crearLink(token, `${BASE}/diagnostico/${ordenId}`);
}

export async function pagarCotizacion(token, ordenId) {
  window.location.href = await crearLink(token, `${BASE}/cotizacion/${ordenId}`);
}
