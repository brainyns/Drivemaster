// Qué puede confirmar el taller de cada pedido, con la regla del backend. Sin componentes: la prueban los tests.

export const SIN_DESPACHO = ["PENDIENTE", "EN_PREPARACION"];
export const TEXTO_SIN_DESPACHO = "La tienda aún no despacha";

/**
 * pedidos: los de la orden; elegidos: ids marcados por el taller. Solo un pedido DESPACHADO se puede marcar. La orden
 * avanza cuando ninguno sigue por despachar o por confirmar; si alguna tienda no pudo despachar, también pide aceptarlo.
 */
export function estadoRecibidos(pedidos = [], elegidos = [], aceptaFaltantes = false) {
  const despachados = pedidos.filter((p) => p.estado === "DESPACHADO");
  const sinDespacho = pedidos.filter((p) => SIN_DESPACHO.includes(p.estado));
  const noDespachados = pedidos.filter((p) => p.estado === "NO_DESPACHADO");
  const validos = despachados.filter((p) => elegidos.includes(p.id)).map((p) => p.id);
  const ningunoLlego = pedidos.length > 0 && noDespachados.length === pedidos.length;
  const avanza = sinDespacho.length === 0 && validos.length === despachados.length;
  const pideFaltantes = avanza && noDespachados.length > 0 && !ningunoLlego;
  const puedeConfirmar = !ningunoLlego && (validos.length > 0 || avanza) && (!pideFaltantes || aceptaFaltantes);
  return { despachados, sinDespacho, noDespachados, validos, ningunoLlego, avanza, pideFaltantes, puedeConfirmar };
}

/** true si el taller tiene algo que confirmar o terminar: sin eso el botón ni abre la confirmación. */
export const hayQueConfirmar = (pedidos = []) => {
  const { despachados, avanza, ningunoLlego } = estadoRecibidos(pedidos);
  return !ningunoLlego && (despachados.length > 0 || avanza);
};

/** El aviso después de confirmar, según los pedidos que devolvió el backend. */
export function avisoRecibidos(pedidos = []) {
  const esperando = pedidos.filter((p) => SIN_DESPACHO.includes(p.estado) || p.estado === "DESPACHADO");
  if (esperando.length === 0) return { tono: "ok", texto: "Repuestos confirmados. Ya puedes iniciar la instalación." };
  return { tono: "info", texto: `Pedido confirmado. La solicitud sigue esperando a ${esperando.map((p) => p.tiendaNombre).join(", ")}.` };
}
