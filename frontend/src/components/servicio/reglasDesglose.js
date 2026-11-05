// Cómo se nombra el dinero de un negocio: venta, comisión de DriveMaster y lo que recibe. Los montos vienen calculados del
// backend. Sin componentes: lo prueban los tests con node --test.
import { formatFecha, formatPrecio } from "./estadosServicio.js";

/** Estado del pago para el negocio. Lo simulado no se llama pagado: no movió dinero. */
export const ESTADOS_PAGO_NEGOCIO = {
  EN_ESPERA: { label: "Falta que se entregue", tono: "aviso" },
  LISTO: { label: "Listo", tono: "info" },
  PAGADO: { label: "Ya te pagaron", tono: "ok" },
  SIMULADO: { label: "Simulado", tono: "apagado" },
  ANULADO: { label: "Cancelado", tono: "error" },
  // Solo del total de una orden: una línea ya se pagó y la otra no
  PARCIAL: { label: "Pagado en parte", tono: "proceso" },
  SIN_PAGOS: { label: "Sin pagos", tono: "apagado" },
};

const CONCEPTOS = { DIAGNOSTICO: "Diagnóstico", COTIZACION: "Cotización", CATALOGO: "Venta de catálogo" };

export const textoConceptoPago = (concepto) => CONCEPTOS[concepto] || "Pago";

const porcentaje = (pct) => `${String(pct).replace(".", ",")} %`;

const NOTA_SIN_DESCUENTO = { TODAVIA_NO: "No se descuenta todavía", NO_EN_ESTE_PAGO: "No se descontó en este pago" };

/** La fila de la comisión de una línea. La nota explica la base: sin ella, el 10 % de la venta con IVA parece mal calculado. */
export function filaComision(l) {
  const etiqueta = l.comisionPct != null ? `Comisión DriveMaster (${porcentaje(l.comisionPct)})` : "Comisión DriveMaster";
  if (l.estadoComision === "DESCONTADA") {
    return { etiqueta, valor: `− ${formatPrecio(l.comision)}`, nota: `${porcentaje(l.comisionPct)} sobre ${formatPrecio(l.comisionBase)} sin IVA` };
  }
  return { etiqueta, valor: formatPrecio(l.comision), nota: NOTA_SIN_DESCUENTO[l.estadoComision] };
}

/** La fila de la comisión en los totales de un informe. */
export function filaComisionResumen(r) {
  return r.comisionSeDescuenta
    ? { etiqueta: "Comisión DriveMaster", valor: `− ${formatPrecio(r.comisionDescontada)}`, nota: "Sobre el valor sin IVA de cada venta" }
    : { etiqueta: "Comisión DriveMaster", valor: formatPrecio(r.comision), nota: NOTA_SIN_DESCUENTO.TODAVIA_NO };
}

/** La comisión de toda una orden: lo descontado si hubo descuento; si no, cuánto corresponde y por qué no se restó. */
export function filaComisionOrden(r) {
  const etiqueta = "Comisión DriveMaster";
  if (r.comisionDescontada > 0) return { etiqueta, valor: `− ${formatPrecio(r.comisionDescontada)}`, nota: "Sobre el valor sin IVA" };
  return { etiqueta, valor: formatPrecio(r.comision), nota: r.comisionSeDescuenta ? NOTA_SIN_DESCUENTO.NO_EN_ESTE_PAGO : NOTA_SIN_DESCUENTO.TODAVIA_NO };
}

/** "Incluye $ 19.000 de IVA": ese dinero no es ganancia. Sin la cifra, o si es 0, no dice nada. */
export const notaIva = (iva) => (iva > 0 ? `Incluye ${formatPrecio(iva)} de IVA` : undefined);

/**
 * El recibo de una orden. Diagnóstico + mano de obra − anticipo = total; "Cotización pagada" es mano de obra − anticipo,
 * una parte del cálculo y no otro sumando. Una orden con solo el diagnóstico no muestra lo que no pasó.
 */
export function filasOrden(r, etiquetaRecibe) {
  const conCotizacion = r.manoDeObra > 0 || r.anticipo > 0 || r.repuestosTaller > 0;
  return [
    { etiqueta: "Diagnóstico pagado", valor: formatPrecio(r.diagnosticoPagado) },
    ...(conCotizacion ? [
      { etiqueta: "Mano de obra con IVA", valor: formatPrecio(r.manoDeObra) },
      ...(r.repuestosTaller > 0 ? [{ etiqueta: "Repuestos del taller con IVA", valor: formatPrecio(r.repuestosTaller) }] : []),
      { etiqueta: "Adelanto del diagnóstico", valor: `− ${formatPrecio(r.anticipo)}` },
      { etiqueta: "Cotización pagada", valor: formatPrecio(r.cotizacionPagada), nota: r.repuestosTaller > 0 ? "Mano de obra y repuestos, menos el adelanto" : "Mano de obra menos el adelanto", tipo: "parte" },
    ] : []),
    { etiqueta: "Total generado por el taller", valor: formatPrecio(r.totalGenerado), nota: notaIva(r.iva), tipo: "total" },
    filaComisionOrden(r),
    { etiqueta: etiquetaRecibe, valor: formatPrecio(r.recibe), tipo: "final" },
  ];
}

export const hayPagosCliente = (c) => !!c && (c.total > 0 || c.devolucionPendiente > 0 || c.devuelto > 0);

/** Lo que pagó el cliente. Con devoluciones, cierra en lo que de verdad le costó el servicio. */
export function filasPagosCliente(c) {
  const devoluciones = c.devolucionPendiente + c.devuelto;
  return [
    { etiqueta: "Diagnóstico pagado", valor: formatPrecio(c.diagnostico) },
    { etiqueta: "Cotización pagada", valor: formatPrecio(c.cotizacion) },
    { etiqueta: "Total pagado", valor: formatPrecio(c.total), tipo: devoluciones > 0 ? "total" : "final" },
    ...(c.devolucionPendiente > 0 ? [{ etiqueta: "Te devolveremos", valor: `− ${formatPrecio(c.devolucionPendiente)}` }] : []),
    ...(c.devuelto > 0 ? [{ etiqueta: "Te devolvimos", valor: `− ${formatPrecio(c.devuelto)}` }] : []),
    ...(devoluciones > 0 ? [{ etiqueta: "Costo final del servicio", valor: formatPrecio(c.total - devoluciones), tipo: "final" }] : []),
  ];
}

/** La nota del total de un periodo en Informes. */
export function notaTotalPeriodo(ingresos) {
  const ordenes = ingresos.ordenes > 0 ? `${ingresos.ordenes} ${ingresos.ordenes === 1 ? "orden" : "órdenes"}` : null;
  return [ordenes, notaIva(ingresos.iva)?.toLowerCase().replace("iva", "IVA")].filter(Boolean).join(" · ") || undefined;
}

/** Qué pasa con este dinero, en una frase. */
export function textoPago(l) {
  if (l.estado === "PAGADO") {
    return ["Transferido", l.fechaPago && `el ${formatFecha(l.fechaPago)}`, l.referencia && `· Ref. ${l.referencia}`].filter(Boolean).join(" ");
  }
  if (l.estado === "LISTO") return "DriveMaster te lo transfiere cuando apruebe el pago.";
  if (l.estado === "SIMULADO") return "Prueba del sistema: no se movió dinero.";
  if (l.estado === "ANULADO") return "Este pago no se hará.";
  return "Se habilita cuando se confirme la entrega.";
}
