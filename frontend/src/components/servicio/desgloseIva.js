// Desglose del IVA de una cotización, con la misma regla que el backend (CalculadoraCotizacion.lineasIva), que la usa
// en el correo y el PDF: si los porcentajes de repuestos y mano de obra coinciden, una sola línea "IVA (19 %)"; si no,
// una por tipo, solo de los tipos que tiene la cotización.

export const formatPct = (v) => (v == null ? "" : `${Number.isInteger(v) ? v : v.toFixed(1).replace(".", ",")} %`);

/** [{ texto, monto }]. Las cotizaciones anteriores al IVA por taller solo traen ivaPct: vale para los dos tipos. */
export function lineasIva(c) {
  const pctRepuestos = c.ivaRepuestosPct ?? c.ivaPct ?? 0;
  const pctManoObra = c.ivaManoObraPct ?? c.ivaPct ?? 0;
  if (pctRepuestos === pctManoObra) return [{ texto: `IVA (${formatPct(pctRepuestos)})`, monto: c.iva }];
  const lineas = [];
  if (c.subtotalRepuestos > 0) lineas.push({ texto: `IVA repuestos (${formatPct(pctRepuestos)})`, monto: c.ivaRepuestos });
  if (c.subtotalManoObra > 0) lineas.push({ texto: `IVA mano de obra (${formatPct(pctManoObra)})`, monto: c.ivaManoObra });
  return lineas;
}
