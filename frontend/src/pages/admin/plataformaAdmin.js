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
    { clave: "por-vencer", n: a.despachosPorVencer, tono: "aviso", estado: "Por vencer", pagina: "pedidos-envios", preset: "por-vencer",
      titulo: (n) => `${num(n)} ${plural(n, "despacho por vencer", "despachos por vencer")}`,
      detalle: "Les queda menos del 25 % del plazo de despacho.", accion: "Ver pedidos" },
    { clave: "sin-retirar", n: a.retirosSinRecoger, tono: "aviso", estado: "Sin retirar", pagina: "pedidos-envios", preset: "sin-retirar",
      titulo: (n) => `${num(n)} ${plural(n, "pedido sin retirar", "pedidos sin retirar")}`,
      detalle: "Listos en la tienda hace 3 días o más; a los 15 pasan a tu revisión.", accion: "Ver pedidos" },
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

/** Filas de una lista de barras (p. ej. órdenes por estado) con su parte del total. */
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
  // Fuera de servicio sin borrar nada (la antigua tienda DriveMaster, duplicados): no vende ni cuenta en los indicadores
  ARCHIVADO: { label: "Archivado", plural: "Archivados", tono: "apagado" },
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
// Las alertas del monitor (las mismas reglas del backend, SemaforoPedido) y el atajo con que llegan del dashboard
export const ALERTAS_PEDIDO = [
  { valor: "", label: "Todas" },
  { valor: "POR_VENCER", label: "Por vencer", preset: "por-vencer" },
  { valor: "ATRASADO", label: "Atrasados", preset: "atrasados" },
  { valor: "SIN_RETIRAR", label: "Sin retirar", preset: "sin-retirar" },
];

/** Filtros del monitor; preset es el atajo del dashboard ("atrasados", "por-vencer" o "sin-retirar"). */
export function filtrosInicialesPedidos(preset) {
  const alerta = ALERTAS_PEDIDO.find((a) => a.preset && a.preset === preset)?.valor || "";
  return { estado: "", tiendaId: "", entrega: "", alerta };
}

const TONO_SEMAFORO = { VERDE: "ok", AMARILLO: "aviso", ROJO: "error" };
const diaMes = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short" }) : "");

/**
 * Los chips de la columna Alerta: el semáforo del despacho (verde a tiempo, amarillo con menos del 25 % del plazo,
 * rojo vencido), el de "sin retirar" (desde los 3 días amarillo, a los 15 rojo y en revisión) y el problema de envío.
 */
export function chipsAlerta(p = {}) {
  const chips = [];
  const plazo = p.plazoDespacho ? ` · plazo ${diaMes(p.plazoDespacho)}` : "";
  if (p.semaforoDespacho === "ROJO") chips.push({ tono: "error", texto: `Atrasado${plazo}` });
  else if (p.semaforoDespacho === "AMARILLO") chips.push({ tono: "aviso", texto: `Por vencer${plazo}` });
  else if (p.semaforoDespacho === "VERDE") chips.push({ tono: "ok", texto: `A tiempo${plazo}` });
  if (p.semaforoRetiro) {
    const dias = p.diasSinRetirar || 0;
    const texto = p.semaforoRetiro === "VERDE" ? "Esperando que lo recojan"
      : `Sin retirar hace ${dias} ${dias === 1 ? "día" : "días"}${p.semaforoRetiro === "ROJO" ? " · en revisión" : ""}`;
    chips.push({ tono: TONO_SEMAFORO[p.semaforoRetiro], texto });
  }
  if (p.problemaAbierto) chips.push({ tono: "error", texto: "Problema de envío" });
  return chips;
}

/** Una fila pide atención si algo está en amarillo o rojo, o tiene un problema de envío. */
export const filaConAlerta = (p = {}) => chipsAlerta(p).some((c) => c.tono === "aviso" || c.tono === "error");

/**
 * Las pestañas de Pedidos y envíos: los 9 estados agrupados en 6 (los mismos grupos que acepta el backend en estado,
 * PlataformaConsultasService.GRUPOS_PEDIDO). "Cancelados" no es "problema de envío abierto": ese es un aviso aparte.
 */
export const GRUPOS_PEDIDO = [
  { valor: "", label: "Todos", estados: [] },
  { valor: "POR_DESPACHAR", label: "Por despachar", estados: ["PENDIENTE", "EN_PREPARACION"] },
  { valor: "EN_CAMINO", label: "En camino", estados: ["DESPACHADO"] },
  { valor: "POR_RETIRAR", label: "Por retirar", estados: ["LISTO_PARA_RETIRAR"] },
  { valor: "COMPLETADOS", label: "Completados", estados: ["RECIBIDO", "RETIRADO", "ENTREGADO"] },
  { valor: "CANCELADOS", label: "Cancelados", estados: ["NO_DESPACHADO", "NO_ENTREGADO"] },
];

/** Pedidos por grupo, sumando el conteo por estado que manda el backend (Todos: la suma de todos). */
export function conteoGrupos(porEstado = {}) {
  const total = Object.values(porEstado).reduce((s, n) => s + (Number(n) || 0), 0);
  return Object.fromEntries(GRUPOS_PEDIDO.map((g) => [g.valor,
    g.estados.length ? g.estados.reduce((s, e) => s + (Number(porEstado[e]) || 0), 0) : total]));
}

/** Formas de entrega del monitor. */
export const ENTREGAS_PEDIDO = [
  { valor: "", label: "Envío y retiro" },
  { valor: "ENVIO", label: "Envío a domicilio" },
  { valor: "RETIRO", label: "Retiro en tienda" },
];

/** Hay algún filtro puesto (para mostrar "Limpiar filtros"). El orden no cuenta: no filtra. */
export const hayFiltrosPedidos = (filtros = {}, fechas = {}) =>
  ["estado", "tiendaId", "entrega", "alerta"].some((k) => (filtros[k] || "") !== "") || !!fechas.desde || !!fechas.hasta;

/** Estados con pedidos, en el orden en que avanza un pedido (los desconocidos al final). */
export function estadosConPedidos(porEstado = {}) {
  const orden = Object.keys(ESTADOS_PEDIDO);
  return [...orden.filter((e) => porEstado[e]), ...Object.keys(porEstado).filter((e) => !orden.includes(e) && porEstado[e])];
}
