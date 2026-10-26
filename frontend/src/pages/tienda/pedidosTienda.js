// Pedidos de la tienda: filtros, orden por urgencia, búsqueda, historial y la acción de cada estado. Sin componentes:
// lo prueban los tests con node --test. Las reglas de cada acción son las del backend (PedidoTiendaService); aquí
// solo se decide qué mostrar.

import { formatFechaHora, refSolicitud } from "../../components/servicio/estadosServicio.js";

export const esCatalogo = (p) => p.origen === "CATALOGO";
export const esRetiro = (p) => esCatalogo(p) && p.entrega === "RETIRO";
export const esEnvio = (p) => esCatalogo(p) && p.entrega === "ENVIO";
export const porDespachar = (p) => p.estado === "PENDIENTE" || p.estado === "EN_PREPARACION";
export const tieneEncargo = (p) => (p.items || []).some((i) => i.disponibilidad === "ENCARGO");
export const plazoVencido = (p, ahora = Date.now()) =>
  porDespachar(p) && !!p.plazoDespachoHasta && new Date(p.plazoDespachoHasta).getTime() < ahora;
/** Pide algo a la tienda: prepararlo, despacharlo o entregarlo con el código. */
export const pideAccion = (p) => porDespachar(p) || p.estado === "LISTO_PARA_RETIRAR";

/**
 * Pestañas en este orden. Las principales son el trabajo del día ("Por atender" junta todo lo que pide acción y abre
 * la página); las secundarias (principal: false) son consultas e historial y van como filtros aparte.
 */
export const FILTROS = [
  { key: "ATENDER", label: "Por atender", principal: true, filtro: pideAccion,
    ayuda: "Por atender incluye los pedidos nuevos, los que están en preparación y los listos para entregar." },
  { key: "PREPARACION", label: "En preparación", principal: true, filtro: (p) => p.estado === "EN_PREPARACION" },
  { key: "LISTOS", label: "Listos para entregar", principal: true, filtro: (p) => p.estado === "LISTO_PARA_RETIRAR" },
  { key: "EN_CAMINO", label: "En camino", principal: true, filtro: (p) => p.estado === "DESPACHADO" },
  { key: "NUEVOS", label: "Nuevos", filtro: (p) => p.estado === "PENDIENTE" },
  { key: "ENTREGADOS", label: "Entregados", filtro: (p) => ["RECIBIDO", "RETIRADO", "ENTREGADO"].includes(p.estado) },
  { key: "CANCELADOS", label: "Cancelados", filtro: (p) => ["NO_DESPACHADO", "NO_ENTREGADO"].includes(p.estado) },
  { key: "TODOS", label: "Todos", filtro: () => true },
];

export const VACIOS = {
  ATENDER: ["Estás al día", "No tienes pedidos por preparar, despachar ni entregar. Te avisamos por correo cuando llegue uno."],
  NUEVOS: ["Sin pedidos nuevos", "Los pedidos pagados llegan aquí y te avisamos por correo."],
  PREPARACION: ["Nada en preparación", "Aquí quedan los pedidos por encargo mientras consigues el repuesto."],
  LISTOS: ["Nada listo para entregar", "Cuando marques un pedido de retiro como listo, espera aquí al cliente y su código."],
  EN_CAMINO: ["Nada en camino", "Los envíos despachados esperan aquí la confirmación del cliente."],
  ENTREGADOS: ["Aún sin entregas", "Los pedidos entregados, retirados o recibidos quedan aquí como historial."],
  CANCELADOS: ["Ningún pedido cancelado", "Aquí quedan los que no se pudieron despachar o entregar."],
  TODOS: ["Aún no tienes pedidos", "Cuando un cliente o un taller compre tus productos, el pedido aparece aquí."],
};

const t = (f) => (f ? new Date(f).getTime() : 0);

/**
 * Lo urgente primero: plazos vencidos, luego por despachar según su plazo, luego los listos para retirar (el
 * cliente puede llegar en cualquier momento) y al final el resto, del más reciente al más antiguo.
 */
export function ordenarPorUrgencia(pedidos, ahora = Date.now()) {
  const grupo = (p) => (plazoVencido(p, ahora) ? 0 : porDespachar(p) ? 1 : p.estado === "LISTO_PARA_RETIRAR" ? 2 : 3);
  return [...pedidos].sort((a, b) => {
    const g = grupo(a) - grupo(b);
    if (g !== 0) return g;
    if (grupo(a) <= 1) return (t(a.plazoDespachoHasta) || Infinity) - (t(b.plazoDespachoHasta) || Infinity);
    if (grupo(a) === 2) return t(a.fechaListoRetiro) - t(b.fechaListoRetiro);
    return t(b.fechaCreacion) - t(a.fechaCreacion);
  });
}

/**
 * Para quién es el pedido. Del cliente que compra solo se ve lo necesario: en un envío, quien recibe; en un retiro,
 * el nombre y la inicial del apellido que manda el backend ("Laura M."). El texto genérico es solo el respaldo de
 * pedidos sin nombre.
 */
export function destinatario(p) {
  if (esEnvio(p)) return p.envioReceptor || "Cliente";
  if (esRetiro(p)) return p.clienteNombre || "Cliente que retira en tu tienda";
  return p.tallerNombre || "Taller";
}

export function metodoEntrega(p) {
  return esRetiro(p) ? "Retiro en tu tienda" : esEnvio(p) ? "Envío a domicilio" : "Entrega en el taller";
}

export const cantidadProductos = (p) => (p.items || []).reduce((s, i) => s + (Number(i.cantidad) || 0), 0);

/** Busca por número de pedido, quién recibe, taller, vehículo o producto. */
export function coincide(p, termino) {
  const q = (termino || "").trim().toLowerCase().replace(/^#/, "");
  if (!q) return true;
  return [refSolicitud(p.id), p.id, p.envioReceptor, p.clienteNombre, p.tallerNombre, p.vehiculoDescripcion,
    ...(p.items || []).map((i) => i.productoNombre)]
    .some((x) => x && String(x).toLowerCase().includes(q));
}

export const contar = (pedidos) => Object.fromEntries(FILTROS.map((f) => [f.key, pedidos.filter(f.filtro).length]));

/** Subtotal de los productos: el del pedido o, si no viene, la suma de sus ítems. */
export function subtotal(p) {
  if (p.subtotal != null) return p.subtotal;
  return (p.items || []).reduce((s, i) => s + (Number(i.subtotal) || 0), 0);
}

/** Historial del pedido armado con sus fechas (el pedido no guarda eventos): del más reciente al más antiguo. */
export function historialPedido(p) {
  const pasos = [
    [p.fechaCreacion, "Pedido pagado", esCatalogo(p) ? "Compra directa del catálogo" : `Para ${p.tallerNombre || "un taller"}`],
    [p.fechaPreparacion, "En preparación", "Consiguiendo el repuesto por encargo"],
    [p.fechaListoRetiro, "Listo para retirar", "Avisamos al cliente"],
    [p.fechaDespacho, "Despachado", p.notaDespacho || null],
    [p.fechaRetirado, "Retirado por el cliente", "Código de retiro validado"],
    [p.fechaRecibido, "Recibido en el taller", null],
    [p.fechaRecibidoCliente, "Entregado", p.recepcionAutomatica ? "Confirmado solo al vencer el plazo" : "Confirmado por el cliente"],
    [p.fechaProblema, "El cliente reportó un problema", p.problemaDetalle || null, "error"],
    [p.fechaResolucionProblema, "DriveMaster resolvió el problema", p.motivoResolucion || null],
    [p.fechaNoDespacho, "No se pudo despachar", p.motivoNoDespacho || null, "error"],
  ];
  return pasos
    .filter(([fecha]) => fecha)
    .map(([fecha, titulo, detalle, tono]) => ({ fecha, titulo, detalle, tono, cuando: formatFechaHora(fecha) }))
    .sort((a, b) => t(b.fecha) - t(a.fecha));
}

/**
 * La acción del estado actual (la que va destacada y en la barra del celular):
 * VALIDAR (código de retiro), LISTO (retiro), DESPACHAR (abre el formulario) o null si no hay nada que hacer.
 */
export function accionPrincipal(p) {
  if (p.estado === "LISTO_PARA_RETIRAR") return { tipo: "VALIDAR", texto: "Validar código y entregar" };
  if (!porDespachar(p)) return null;
  return esRetiro(p)
    ? { tipo: "LISTO", texto: "Marcar como listo para retirar" }
    : { tipo: "DESPACHAR", texto: "Marcar despachado" };
}

/** Texto del plazo para despachar o dejar listo, o null si no aplica. */
export function textoPlazo(p, ahora = Date.now()) {
  if (!porDespachar(p) || !p.plazoDespachoHasta) return null;
  if (plazoVencido(p, ahora)) return `Plazo vencido el ${formatFechaHora(p.plazoDespachoHasta)}`;
  return `${esRetiro(p) ? "Déjalo listo" : "Despáchalo"} antes del ${formatFechaHora(p.plazoDespachoHasta)}`;
}
