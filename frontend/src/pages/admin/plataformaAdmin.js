// Panel de la plataforma (SUPERADMIN): lo que las pantallas calculan con las respuestas de /api/plataforma. Sin
// componentes: lo prueban los tests con node --test. No agrega reglas de negocio: ordena y nombra lo que ya llega.

const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);
const cop = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);
const plural = (n, uno, varios) => (n === 1 ? uno : varios);

/**
 * Lo urgente del dashboard, en el orden en que conviene atenderlo: primero los negocios que esperan aprobación y los
 * pedidos con problema, después el dinero. Solo lo que tiene casos. pagina y preset son los de irA.
 */
export function urgentesDashboard(a = {}) {
  const items = [
    { clave: "aprobar", n: a.negociosPorAprobar, tono: "aviso", estado: "Por aprobar", pagina: "aprobaciones",
      titulo: (n) => `${num(n)} ${plural(n, "negocio espera", "negocios esperan")} aprobación`,
      detalle: "Tiendas y talleres registrados que esperan tu revisión para empezar a operar.", accion: "Revisar" },
    { clave: "problemas", n: a.problemasEnvioAbiertos, tono: "error", estado: "Pedido con problema", pagina: "problemas-envios",
      titulo: (n) => `${num(n)} ${plural(n, "problema de envío abierto", "problemas de envío abiertos")}`,
      detalle: "Pedidos con un problema de entrega reportado que todavía no tiene resolución.", accion: "Resolver" },
    { clave: "atrasados", n: a.despachosAtrasados, tono: "error", estado: "Atrasado", pagina: "pedidos-envios", preset: "atrasados",
      titulo: (n) => `${num(n)} ${plural(n, "despacho atrasado", "despachos atrasados")}`,
      detalle: "Pedidos pendientes o en preparación que pasaron el plazo de despacho.", accion: "Ver pedidos" },
    { clave: "fallidas", n: a.liquidacionesFallidas, tono: "error", estado: "Pago fallido", pagina: "liquidaciones",
      titulo: (n) => `${num(n)} ${plural(n, "liquidación fallida", "liquidaciones fallidas")}`,
      detalle: "Pagos a talleres o tiendas que no se pudieron completar.", accion: "Revisar" },
    { clave: "reembolsos", n: a.reembolsosPendientes, tono: "error", estado: "Reembolso", pagina: "liquidaciones",
      titulo: (n) => `${num(n)} ${plural(n, "reembolso pendiente", "reembolsos pendientes")}`,
      detalle: `${cop(a.montoReembolsosPendientes)} por devolver a clientes.`, accion: "Revisar" },
    { clave: "liquidar", n: a.liquidacionesPendientes, tono: "info", estado: "Por pagar", pagina: "liquidaciones",
      titulo: (n) => `${num(n)} ${plural(n, "liquidación por pagar", "liquidaciones por pagar")}`,
      detalle: "Dinero ya habilitado para pagar a talleres y tiendas.", accion: "Ver liquidaciones" },
  ];
  return items.filter((x) => (x.n || 0) > 0).map(({ titulo, ...x }) => ({ ...x, titulo: titulo(x.n) }));
}

/** Filas de una lista de barras (órdenes por estado, suscripciones por plan) con su parte del total. */
export function barras(conteos = {}, etiquetas = {}, orden = []) {
  const claves = [
    ...orden.filter((k) => conteos[k]),
    ...Object.keys(conteos).filter((k) => !orden.includes(k) && conteos[k]),
  ];
  const total = claves.reduce((s, k) => s + conteos[k], 0);
  return claves.map((k) => ({ clave: k, etiqueta: etiquetas[k] || k, n: conteos[k], pct: total ? (conteos[k] / total) * 100 : 0 }));
}

// ─── Tiendas y talleres ─────────────────────────────────────────────────────

/** Estado de aprobación con su texto y tono de ChipEstado. */
export const ESTADOS_NEGOCIO = {
  PENDIENTE: { label: "Pendiente", plural: "Pendientes", tono: "aviso" },
  APROBADO: { label: "Aprobado", plural: "Aprobados", tono: "ok" },
  RECHAZADO: { label: "Rechazado", plural: "Rechazados", tono: "error" },
};

export const TIPOS_NEGOCIO = [
  { valor: "", label: "Tiendas y talleres" },
  { valor: "TIENDA", label: "Tiendas" },
  { valor: "TALLER", label: "Talleres" },
];

/** Valor del filtro de plan para los negocios sin suscripción activa (el backend lo entiende igual). */
export const SIN_PLAN = "SIN_PLAN";

// Los datos que pide el backend para operar (PlataformaNegociosService.faltantesTienda / faltantesTaller)
const DATOS_PERFIL = { TIENDA: 3, TALLER: 4 };

/** Perfil completo: cuántos de los datos obligatorios tiene, el porcentaje y qué le falta. */
export function perfilNegocio(n = {}) {
  const faltan = n.datosPorCompletar || [];
  const total = DATOS_PERFIL[n.tipo] || Math.max(faltan.length, 1);
  const completos = Math.max(0, total - faltan.length);
  return { completos, total, pct: (completos / total) * 100, completo: faltan.length === 0, faltan };
}

/** Estado que se muestra: el de aprobación y, si está aprobado pero apagado, "En pausa". */
export function estadoNegocio(n = {}) {
  if (n.estado === "APROBADO" && n.activo === false) return { label: "En pausa", tono: "apagado" };
  return ESTADOS_NEGOCIO[n.estado] || { label: n.estado || "Sin estado", tono: "info" };
}

/** Filtros al llegar desde el dashboard ("TIENDA" o "TALLER"); cualquier otro valor, sin filtro. */
export function filtrosInicialesNegocios(preset) {
  return { tipo: preset === "TIENDA" || preset === "TALLER" ? preset : "", estado: "", plan: "" };
}

/** Lo que muestra la columna Actividad: productos de la tienda u órdenes del taller. */
export function actividadNegocio(n = {}) {
  if (n.tipo === "TIENDA") {
    const t = n.productos || 0;
    return { texto: `${num(t)} ${plural(t, "producto", "productos")}`, detalle: `${num(n.productosActivos || 0)} ${plural(n.productosActivos || 0, "activo", "activos")}` };
  }
  const t = n.ordenes || 0;
  return { texto: `${num(t)} ${plural(t, "orden", "órdenes")}`, detalle: `${num(n.ordenesEntregadas || 0)} ${plural(n.ordenesEntregadas || 0, "entregada", "entregadas")}` };
}

// ─── Aprobaciones ───────────────────────────────────────────────────────────

/** Estado de revisión: los negocios anteriores al marketplace no tienen estado y cuentan como aprobados. */
export const estadoRevision = (n = {}) => n.estadoAprobacion || "APROBADO";

/** Chip de la fila: pendiente o rechazado; un aprobado dice si está activo o inactivo. */
export function chipRevision(n = {}) {
  const e = estadoRevision(n);
  if (e === "PENDIENTE") return { label: "Pendiente", tono: "aviso" };
  if (e === "RECHAZADO") return { label: "Rechazado", tono: "error" };
  return n.activo ? { label: "Activo", tono: "ok" } : { label: "Inactivo", tono: "apagado" };
}

export const correoNegocio = (n = {}) => n.correoUsuario || n.correoContacto || "";

/** Sin tildes ni mayúsculas, para buscar "Bogotá" escribiendo "bogota". */
const normalizar = (t) => (t || "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();

/** Cuántos hay en cada estado con el tipo elegido ("" = ambos). */
export function conteoRevision(negocios = [], tipo = "") {
  const c = { PENDIENTE: 0, APROBADO: 0, RECHAZADO: 0 };
  negocios.forEach((n) => { if (!tipo || n.tipo === tipo) c[estadoRevision(n)] = (c[estadoRevision(n)] || 0) + 1; });
  return c;
}

/** Los de un estado y tipo que coinciden con la búsqueda (nombre, NIT o correo), los más recientes primero. */
export function filtrarRevision(negocios = [], { estado = "PENDIENTE", tipo = "", busqueda = "" } = {}) {
  const q = normalizar(busqueda.trim());
  return negocios
    .filter((n) => estadoRevision(n) === estado && (!tipo || n.tipo === tipo))
    .filter((n) => !q || [n.nombre, n.nit, correoNegocio(n)].some((v) => normalizar(v).includes(q)))
    .sort((a, b) => new Date(b.fechaRegistro || 0) - new Date(a.fechaRegistro || 0));
}

// ─── Pedidos y envíos ───────────────────────────────────────────────────────

/** Estados de un pedido de tienda en el orden en que avanza, con el formato de catálogo de ChipEstado. */
export const ESTADOS_PEDIDO = {
  PENDIENTE: { label: "Por despachar", tono: "aviso" },
  EN_PREPARACION: { label: "En preparación", tono: "proceso" },
  DESPACHADO: { label: "Despachado", tono: "proceso" },
  LISTO_PARA_RETIRAR: { label: "Listo para retirar", tono: "proceso" },
  RECIBIDO: { label: "Recibido", tono: "ok" },
  RETIRADO: { label: "Retirado", tono: "ok" },
  ENTREGADO: { label: "Entregado", tono: "ok" },
  NO_DESPACHADO: { label: "No despachado", tono: "error" },
  NO_ENTREGADO: { label: "No entregado", tono: "error" },
};

/** Referencia corta y legible del pedido (los últimos 6 caracteres del id). */
export const refPedido = (id) => `#${String(id || "").slice(-6).toUpperCase()}`;

/** Filtros al llegar desde el dashboard ("atrasados"); cualquier otro valor, sin filtro. */
export function filtrosInicialesPedidos(preset) {
  return { estado: "", tiendaId: "", entrega: "", atrasados: preset === "atrasados" };
}

/** Estados con pedidos, en el orden en que avanza un pedido (los desconocidos al final). */
export function estadosConPedidos(porEstado = {}) {
  const orden = Object.keys(ESTADOS_PEDIDO);
  return [...orden.filter((e) => porEstado[e]), ...Object.keys(porEstado).filter((e) => !orden.includes(e) && porEstado[e])];
}
