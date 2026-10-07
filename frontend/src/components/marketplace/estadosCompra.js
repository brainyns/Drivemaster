// Textos de los estados de una compra del catálogo, vistos por el cliente. Archivo sin
// componentes para no romper Fast Refresh.

export const ESTADO_COMPRA = {
  PENDIENTE_PAGO: { label: "Esperando el pago", tono: "aviso" },
  PAGADA: { label: "Pagada", tono: "ok" },
  NO_APLICADA: { label: "No se pudo completar · reembolso en proceso", tono: "error" },
};

/** Estado de un pedido a una tienda, con lo que sigue. entrega: ENVIO | RETIRO */
export function estadoPedido(p) {
  const retiro = p.entrega === "RETIRO";
  switch (p.estado) {
    case "PENDIENTE":
      return { label: "Pagado", tono: "ok", detalle: "La tienda está alistando tu pedido." };
    case "EN_PREPARACION":
      return { label: "En preparación", tono: "ok", detalle: "La tienda está consiguiendo tu repuesto." };
    case "LISTO_PARA_RETIRAR":
      return { label: "Listo para retirar", tono: "ok", detalle: "Pasa por la tienda con tu código de retiro." };
    case "RETIRADO":
      return { label: "Retirado", tono: "ok", detalle: "Lo retiraste en la tienda." };
    case "DESPACHADO":
      if (p.problemaReportado) return { label: "Problema reportado", tono: "aviso", detalle: "DriveMaster está revisando tu reporte." };
      return { label: "En camino", tono: "ok", detalle: "La tienda lo despachó. Confirma cuando lo recibas." };
    case "ENTREGADO":
      return { label: "Entregado", tono: "ok", detalle: p.recepcionAutomatica ? "Lo dimos por recibido al vencer el plazo." : "Confirmaste que lo recibiste." };
    case "NO_DESPACHADO":
      return { label: "La tienda no pudo despachar", tono: "error", detalle: "Te devolveremos el valor de este pedido." };
    case "NO_ENTREGADO":
      return { label: "No entregado", tono: "error", detalle: "Revisamos tu reporte y te devolveremos el valor de este pedido." };
    default:
      return { label: p.estado, tono: "aviso", detalle: retiro ? "Retiro en tienda." : "Envío a domicilio." };
  }
}

/** Estados en los que el código de retiro todavía sirve. */
export const ESTADOS_CON_CODIGO = ["PENDIENTE", "EN_PREPARACION", "LISTO_PARA_RETIRAR"];
