// Reparte las compras del cliente entre "En curso" y "Anteriores". Une las compras del
// catálogo (/api/catalogo/compras/mias) con las ventas del flujo anterior
// (/api/ventas/mis-ventas). Sin imports ni JSX: lo prueban los tests con node --test.

/** Pedidos a una tienda que todavía se mueven: pagado, en preparación, enviado o listo para retirar. */
export const PEDIDOS_ACTIVOS = ["PENDIENTE", "EN_PREPARACION", "DESPACHADO", "LISTO_PARA_RETIRAR"];

/**
 * Una compra del catálogo está en curso si espera el pago o si al menos uno de sus pedidos
 * sigue activo. NO_APLICADA (reembolso) y las compras con todos sus pedidos cerrados
 * (entregado, retirado, no despachado o no entregado) van a Anteriores.
 */
export function compraEnCurso(c) {
  if (c.estado === "PENDIENTE_PAGO") return true;
  if (c.estado === "NO_APLICADA") return false;
  const pedidos = c.pedidos || [];
  if (pedidos.length === 0) return true;
  return pedidos.some((p) => PEDIDOS_ACTIVOS.includes(p.estado));
}

/** Estados de una venta del flujo anterior, vistos por el cliente. */
export const ESTADO_VENTA_ANTERIOR = {
  PENDIENTE_PAGO: { label: "Esperando el pago", tono: "aviso", enCurso: true },
  PAGADA: { label: "Pagada", tono: "ok", enCurso: true },
  APROBADO: { label: "Aprobada", tono: "ok", enCurso: true },
  EN_CAMINO: { label: "En camino", tono: "ok", enCurso: true },
  ENTREGADO: { label: "Entregada", tono: "ok", enCurso: false },
  COMPLETADA: { label: "Completada", tono: "ok", enCurso: false },
  ANULADA: { label: "Anulada", tono: "error", enCurso: false },
  CANCELADA: { label: "Cancelada", tono: "error", enCurso: false },
};

export function estadoVentaAnterior(v) {
  return ESTADO_VENTA_ANTERIOR[(v.estado || "").toUpperCase()] || { label: v.estado || "Sin estado", tono: "aviso", enCurso: false };
}

/**
 * Solicitudes del carrito anterior (/api/solicitudes/mis-solicitudes) que aún no se cierran:
 * ni rechazadas ni entregadas. Su pago y seguimiento siguen en la pantalla Mis solicitudes.
 */
export function solicitudesSinCerrar(solicitudes = []) {
  return solicitudes.filter((s) => !["RECHAZADO", "ENTREGADO", "COMPLETADA"].includes(s.estado));
}

const fechaCompra =(c) => new Date(c.fechaPago || c.fechaCreacion || 0).getTime();
const fechaVenta = (v) => new Date(v.fecha || 0).getTime();

/**
 * Une y reparte. Las ventas de tipo TALLER se dejan fuera: son el cobro de las órdenes
 * de taller del flujo anterior, que se muestran en Mis servicios (Servicios anteriores).
 * Cada elemento lleva su origen ("catalogo" o "anterior") y se ordena del más reciente al más antiguo.
 */
export function clasificarCompras(compras = [], ventas = []) {
  const todas = [
    ...compras.map((c) => ({ origen: "catalogo", id: c.id, fecha: fechaCompra(c), enCurso: compraEnCurso(c), dato: c })),
    ...ventas.filter((v) => v.tipoVenta !== "TALLER")
      .map((v) => ({ origen: "anterior", id: v.id, fecha: fechaVenta(v), enCurso: estadoVentaAnterior(v).enCurso, dato: v })),
  ].sort((a, b) => b.fecha - a.fecha);
  return { enCurso: todas.filter((x) => x.enCurso), anteriores: todas.filter((x) => !x.enCurso) };
}
