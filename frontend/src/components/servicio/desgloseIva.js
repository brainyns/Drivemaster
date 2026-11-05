// Desglose del IVA de una cotización para mostrar. Los montos vienen calculados del backend; aquí solo se
// presentan. El IVA NO se suma aparte: va incluido en los precios y se informa cuánto lleva la cotización.
// Regla de líneas idéntica a CalculadoraCotizacion (ivaDeLinea / lineaConIva).

import { porcentaje } from "../../utils/formato.js";

export const formatPct = (v) => (v == null ? "" : porcentaje(v));

const valor = (v) => (v == null ? 0 : v);

/** IVA de una línea: repuestos con el IVA del que vende (taller o tienda), mano de obra con el del taller. */
export function ivaDeLinea(c, i) {
  if (!i || i.subtotal == null) return 0;
  const pctRepuestos = c.ivaRepuestosPct ?? c.ivaPct ?? 0;
  const pctManoObra = c.ivaManoObraPct ?? c.ivaPct ?? 0;
  const pct = i.tipo === "REPUESTO" ? (i.origen === "TALLER" ? pctManoObra : pctRepuestos) : pctManoObra;
  return Math.round((i.subtotal * pct) / 100);
}

/** Total de una línea con su IVA (lo que paga el cliente por esa línea). */
export const totalLinea = (c, i) => valor(i?.subtotal) + ivaDeLinea(c, i);

/** Mano de obra con su IVA: el tope hasta donde llega el adelanto del diagnóstico. */
export const manoObraConIva = (c) => valor(c.subtotalManoObra) + valor(c.ivaManoObra);

/** El IVA de la cotización, calculado si no viene ("Incluye IVA de $ X"). */
export const ivaTotal = (c) => valor(c.iva ?? ((c.ivaRepuestos || 0) + (c.ivaManoObra || 0) + (c.ivaRepuestosTaller || 0)));

/** Aclaración de precio por unidad de un repuesto (siempre con IVA). "" si no aplica. */
export function aclaracionUnidad(c, i, fmt) {
  if (i?.tipo !== "REPUESTO" || i.precioUnitario == null) return "";
  const pctRepuestos = c.ivaRepuestosPct ?? c.ivaPct ?? 0;
  const pctManoObra = c.ivaManoObraPct ?? c.ivaPct ?? 0;
  const ivaPct = i.origen === "TALLER" ? pctManoObra : pctRepuestos;
  const base = i.precioUnitario;
  const iva = Math.round((base * ivaPct) / 100);
  const total = base + iva;
  return ivaPct === 0 ? `${fmt(total)} c/u, sin IVA` : `${fmt(total)} c/u con IVA (${fmt(base)} + IVA ${fmt(iva)})`;
}

/**
 * La frase que explica cómo se aplicó el adelanto del diagnóstico. Misma regla que
 * CalculadoraCotizacion.explicacionAdelanto: nunca inventa cifras; sin la tarifa original, describe la aplicada.
 */
export function explicacionAdelanto(c, fmt) {
  const aplicado = valor(c?.anticipoDiagnostico);
  if (!(aplicado > 0)) return null;
  const aplicadoFmt = fmt(aplicado);
  const tarifa = c.diagnosticoPagado;
  if (tarifa != null && tarifa > aplicado) {
    return `Diagnóstico ${fmt(tarifa)} · se usaron ${aplicadoFmt} para pagar la mano de obra.`;
  }
  if (aplicado < manoObraConIva(c)) return `Se descontaron los ${aplicadoFmt} del diagnóstico.`;
  return `Se usaron los ${aplicadoFmt} del diagnóstico para pagar la mano de obra.`;
}

/**
 * Las filas del resumen, en el orden único de la app: Total (con IVA incluido) → Incluye IVA de (informativa)
 * → − Adelanto del diagnóstico (si lo hay) → Total a pagar.
 */
export function totalesCotizacion(c) {
  const filas = [
    { clave: "total", texto: "Total", monto: valor(c.total), tono: "texto" },
    { clave: "iva", texto: "Incluye IVA de", monto: ivaTotal(c), tono: "info" },
  ];
  const adelanto = valor(c.anticipoDiagnostico);
  if (adelanto > 0) filas.push({ clave: "adelanto", texto: "Adelanto del diagnóstico", monto: -adelanto, tono: "descuento" });
  filas.push({ clave: "pagar", texto: "Total a pagar", monto: valor(c.totalAPagar), tono: "final" });
  return filas;
}

/**
 * La línea pequeña bajo el precio de una oferta del buscador (BuscadorRepuestos). propia: es del inventario
 * del taller. El precio mostrado ya lleva IVA incluido cuando la tienda cobra; aquí solo se aclara lo que falta.
 */
export function textoIvaOferta(o, fmt) {
  if (o?.precioFinal == null) return "";
  if (!o.ivaPct) return o.origen === "TALLER" ? "Sin IVA: tu taller no cobra IVA" : "Sin IVA";
  return `${fmt(o.precio)} + IVA ${fmt(o.ivaMonto)}`;
}
