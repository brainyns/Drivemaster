// Reglas de "Mi inventario", sin React, para probarlas con node --test. Las mismas cuentas que el backend
// (InventarioTallerService): el backend manda, esto solo adelanta lo que verá el taller mientras escribe un precio.

const redondeo1 = (n) => Math.round(n * 10) / 10;

/**
 * Las tres cifras que el taller debe ver por separado, por unidad y antes de IVA:
 *  - recargo: cuánto le sumó al costo, sobre el costo;
 *  - margen: qué parte del precio de venta es suya, sobre el precio;
 *  - ganancia: lo que le queda después de la comisión de DriveMaster.
 * La comisión va sobre el precio con el IVA del taller (ivaPct; 0 si no es responsable), como en el backend.
 * null si falta el precio o el costo.
 */
export function cifrasPieza(precio, costo, comisionPct, ivaPct = 0) {
  if (!(precio > 0) || costo == null) return null;
  const conIva = Math.round((precio * (100 + ivaPct)) / 100);
  const comision = Math.round((conIva * comisionPct) / 100);
  const ganancia = precio - costo - comision;
  return {
    recargoPct: costo > 0 ? redondeo1(((precio - costo) * 100) / costo) : null,
    margenPct: redondeo1(((precio - costo) * 100) / precio),
    comision,
    ganancia,
    enPerdida: ganancia < 0,
  };
}

/** Precio para un recargo sobre el costo, en pesos enteros. */
export const precioConRecargo = (costo, recargoPct) => Math.round(costo * (1 + recargoPct / 100));

/** "35 %", "25,9 %", "−5 %". */
export const pct = (v) => (v == null ? "—" : `${v.toLocaleString("es-CO", { maximumFractionDigits: 1 }).replace("-", "−")} %`);

/** Estado de un pedido en camino, en palabras del taller, y qué puede hacer con él. */
export function estadoEnCamino(p) {
  if (p.problemaReportado) return { texto: "Problema reportado", tono: "aviso", detalle: "DriveMaster lo está revisando con la tienda." };
  if (p.puedeConfirmar) {
    return p.entrega === "RETIRO"
      ? { texto: "Retirado", tono: "ok", detalle: "Confirma que ya lo tienes en el taller para que entre a tu inventario." }
      : { texto: "En camino", tono: "info", detalle: "Cuando llegue, confírmalo: así entra a tu inventario y se le paga a la tienda." };
  }
  if (p.estado === "LISTO_PARA_RETIRAR") return { texto: "Listo para retirar", tono: "info", detalle: "Retíralo en la tienda con el código que te llegó por correo." };
  return { texto: "Esperando a la tienda", tono: "neutro", detalle: "La tienda aún no lo despacha." };
}

/** Resumen de los lotes de una pieza: de qué compras vino y cuánto queda de cada una. */
export const lotesConStock = (pieza) => (pieza.lotes || []).filter((l) => l.disponible > 0).length;
