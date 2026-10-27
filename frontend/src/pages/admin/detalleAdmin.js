// Vistas de detalle del SUPERADMIN (pedido, cliente y negocio): cómo se nombra lo que devuelve /api/plataforma/.../{id}.
// Sin componentes: lo prueban los tests con node --test.

/** Quién recibe cada línea del reparto. */
export const BENEFICIARIO = {
  TIENDA: "Tienda",
  TALLER: "Taller",
  PLATAFORMA: "Comisión DriveMaster",
};

/** Estados de una línea del reparto, en palabras del panel de liquidaciones. */
export const ESTADOS_REPARTO = {
  PENDIENTE: { label: "En espera de entrega", tono: "aviso" },
  POR_LIQUIDAR: { label: "Por liquidar", tono: "info" },
  PROCESANDO: { label: "Procesando", tono: "proceso" },
  LIQUIDADO: { label: "Liquidado", tono: "ok" },
  FALLIDO: { label: "Falló el pago", tono: "error" },
  ANULADO: { label: "Anulado", tono: "apagado" },
  INFORMATIVA: { label: "Informativa", tono: "apagado" },
};

/** Estados del pago en Wompi. */
export const ESTADOS_PAGO = {
  PAGADO: { label: "Aprobado", tono: "ok" },
  PENDIENTE: { label: "Pendiente", tono: "aviso" },
  PROCESANDO: { label: "Procesando", tono: "proceso" },
  RECHAZADO: { label: "Rechazado", tono: "error" },
};

/** Cómo llega el pedido: envío, retiro en la tienda o al taller (repuestos de una cotización). */
export function textoEntrega(e) {
  if (!e) return "Sin definir";
  if (e.tipo === "ENVIO") return "Envío a domicilio";
  if (e.tipo === "RETIRO") return "Retiro en la tienda";
  if (e.tipo === "TALLER") return "Entrega en el taller";
  return e.tipo;
}

/**
 * Lo que dice el bloque del reparto: la parte de cada uno y, si el pago cubre varios pedidos, que la comisión es de
 * todo el pago (se registra una sola vez sobre el valor sin IVA ni envío).
 */
export function notaReparto(pago, reparto = []) {
  const n = pago?.pedidosDelPago || 0;
  if (n > 1 && reparto.some((r) => r.delPago)) {
    return `El pago cubre ${n} pedidos: la comisión es de todo el pago, no solo de este pedido.`;
  }
  return null;
}

/** Vehículo en una línea: "Mazda 2 2018 · ABC123 · 45.000 km". */
export function textoVehiculo(v = {}) {
  const km = v.kilometraje != null ? `${new Intl.NumberFormat("es-CO").format(v.kilometraje)} km` : null;
  return [v.descripcion || "Vehículo sin datos", v.placa, km].filter(Boolean).join(" · ");
}

/** Ubicación del negocio en una línea, y el enlace al mapa si tiene coordenadas. */
export function ubicacionNegocio(n = {}) {
  const texto = [n.direccion, n.barrio, n.ciudad].filter((x) => x && String(x).trim()).join(", ");
  const mapa = n.latitud != null && n.longitud != null
    ? `https://www.google.com/maps?q=${encodeURIComponent(`${n.latitud},${n.longitud}`)}`
    : null;
  return { texto, mapa };
}
