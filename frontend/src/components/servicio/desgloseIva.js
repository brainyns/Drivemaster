// Desglose del IVA de una cotización, con la misma regla que el backend (CalculadoraCotizacion.lineasIva), que la usa
// en el correo y el PDF: si los porcentajes de repuestos y mano de obra coinciden, una sola línea "IVA (19 %)"; si no,
// una por tipo, solo de los tipos que tiene la cotización. Los repuestos del inventario del taller llevan el IVA del
// taller: con ellos las líneas dicen de quién es cada IVA.

export const formatPct = (v) => (v == null ? "" : `${Number.isInteger(v) ? v : v.toFixed(1).replace(".", ",")} %`);

/** [{ texto, monto }]. Las cotizaciones anteriores al IVA por taller solo traen ivaPct: vale para los dos tipos. */
export function lineasIva(c) {
  const pctRepuestos = c.ivaRepuestosPct ?? c.ivaPct ?? 0;
  const pctManoObra = c.ivaManoObraPct ?? c.ivaPct ?? 0;
  if (pctRepuestos === pctManoObra) return [{ texto: `IVA (${formatPct(pctRepuestos)})`, monto: c.iva }];
  const piezas = c.subtotalRepuestosTaller > 0;
  const lineas = [];
  if (c.subtotalRepuestos > 0) {
    lineas.push({ texto: `${piezas ? "IVA repuestos de tiendas" : "IVA repuestos"} (${formatPct(pctRepuestos)})`, monto: c.ivaRepuestos });
  }
  if (piezas) lineas.push({ texto: `IVA del taller (${formatPct(pctManoObra)})`, monto: (c.ivaManoObra || 0) + (c.ivaRepuestosTaller || 0) });
  else if (c.subtotalManoObra > 0) lineas.push({ texto: `IVA mano de obra (${formatPct(pctManoObra)})`, monto: c.ivaManoObra });
  return lineas;
}

/**
 * Precio por unidad de un repuesto como lo paga el cliente, si la cotización mezcla dos IVA (misma regla que
 * CalculadoraCotizacion.precioFinal): { base, ivaPct, iva, total } o null si no es repuesto o no hace falta aclararlo.
 */
export function precioFinalLinea(c, i) {
  if (i?.tipo !== "REPUESTO" || i.precioUnitario == null) return null;
  const pctRepuestos = c.ivaRepuestosPct ?? c.ivaPct ?? 0;
  const pctManoObra = c.ivaManoObraPct ?? c.ivaPct ?? 0;
  if (pctRepuestos === pctManoObra) return null;
  const ivaPct = i.origen === "TALLER" ? pctManoObra : pctRepuestos;
  const base = i.precioUnitario;
  const iva = Math.round((base * ivaPct) / 100);
  return { base, ivaPct, iva, total: base + iva };
}

/** "$ 34.510 c/u, sin IVA" o "$ 34.510 c/u con IVA ($ 29.000 + IVA $ 5.510)", como en el PDF. */
export function textoPrecioFinal(p, fmt) {
  if (!p) return "";
  return p.ivaPct === 0 ? `${fmt(p.total)} c/u, sin IVA` : `${fmt(p.total)} c/u con IVA (${fmt(p.base)} + IVA ${fmt(p.iva)})`;
}

/**
 * La línea pequeña bajo el precio final de una oferta del buscador. propia: es del inventario del taller.
 * Ofertas sin precioFinal (backend anterior) no llevan aclaración.
 */
export function textoIvaOferta(o, fmt) {
  if (o?.precioFinal == null) return "";
  if (!o.ivaPct) return o.origen === "TALLER" ? "Sin IVA: tu taller no cobra IVA" : "Sin IVA";
  return `${fmt(o.precio)} + IVA ${fmt(o.ivaMonto)}`;
}
