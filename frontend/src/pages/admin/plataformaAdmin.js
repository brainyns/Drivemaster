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
