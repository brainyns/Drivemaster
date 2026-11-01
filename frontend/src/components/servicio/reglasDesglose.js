// Cómo se nombra el dinero de un negocio: venta, comisión de DriveMaster y lo que recibe. Los montos vienen calculados del
// backend. Sin componentes: lo prueban los tests con node --test.
import { formatFecha, formatPrecio } from "./estadosServicio.js";

/** Estado del pago para el negocio. Lo simulado no se llama pagado: no movió dinero. */
export const ESTADOS_PAGO_NEGOCIO = {
  EN_ESPERA: { label: "En espera", tono: "aviso" },
  LISTO: { label: "Listo", tono: "info" },
  PAGADO: { label: "Pagado", tono: "ok" },
  SIMULADO: { label: "Simulado", tono: "apagado" },
  ANULADO: { label: "Anulado", tono: "error" },
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

/** Qué pasa con este dinero, en una frase. */
export function textoPago(l) {
  if (l.estado === "PAGADO") {
    return ["Transferido", l.fechaPago && `el ${formatFecha(l.fechaPago)}`, l.referencia && `· Ref. ${l.referencia}`].filter(Boolean).join(" ");
  }
  if (l.estado === "LISTO") return "DriveMaster te lo transfiere cuando apruebe la liquidación.";
  if (l.estado === "SIMULADO") return "Prueba del sistema: no se movió dinero.";
  if (l.estado === "ANULADO") return "Este pago no se hará.";
  return "Se habilita cuando se confirme la entrega.";
}
