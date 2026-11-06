// Movimientos del inventario del taller: tipos, el formulario de movimiento manual, el signo de cada fila y a dónde
// lleva cada referencia. Sin componentes: lo prueban los tests con node --test. Las reglas son las del backend
// (StockTallerService.registrarManual y MovimientosTallerService).

import { parsePesos } from "../../utils/formato.js";

/** Lo que el taller registra a mano. sentido: lo que hace con el stock. Ninguno mueve dinero. */
export const CLASES_MANUALES = {
  AJUSTE: { label: "Conteo físico", sentido: "fija", campo: "Stock contado",
    ayuda: "Contaste las piezas: el stock queda en ese número. Si falta, sale del lote más antiguo." },
  CARGA_INICIAL: { label: "Stock inicial", sentido: "entra", campo: "Unidades",
    ayuda: "Piezas que ya tenías antes de DriveMaster. Entran como un lote nuevo con el costo que indiques." },
  MERMA: { label: "Pérdida o daño", sentido: "sale", campo: "Unidades dañadas o perdidas",
    ayuda: "Se dañó o se perdió. Sale del lote más antiguo." },
  DEVOLUCION_TIENDA: { label: "Devolución a la tienda", sentido: "sale", campo: "Unidades que devuelves",
    ayuda: "Se la devolviste a la tienda. El reembolso, si lo hay, lo arreglas con ella." },
  DEVOLUCION_CLIENTE: { label: "Devolución del cliente", sentido: "entra", campo: "Unidades que vuelven",
    ayuda: "Un cliente te la devolvió sin usar. Vuelve al lote del que salió." },
};

/** Cómo se lee cada movimiento en la tabla, también los que hace el sistema. */
export const CLASES = {
  COMPRA: { label: "Compra", tono: "ok" },
  VENTA: { label: "Venta en cotización", tono: "info" },
  CANCELACION: { label: "Devolución por cancelación", tono: "ok" },
  REVERSA: { label: "Reversa de venta", tono: "ok" },
  AJUSTE: { label: "Conteo físico", tono: "aviso" },
  CARGA_INICIAL: { label: "Stock inicial", tono: "ok" },
  MERMA: { label: "Pérdida o daño", tono: "error" },
  DEVOLUCION_TIENDA: { label: "Devolución a la tienda", tono: "error" },
  DEVOLUCION_CLIENTE: { label: "Devolución del cliente", tono: "ok" },
};

export const FILTROS_TIPO = [
  { key: "", label: "Todos" },
  { key: "ENTRADA", label: "Entradas" },
  { key: "SALIDA", label: "Salidas" },
  { key: "AJUSTE", label: "Ajustes" },
];

/** "+3", "−2" o "±0": cantidad siempre positiva, el signo sale de stock antes → después. */
export function cantidadConSigno(m) {
  const d = (Number(m.stockNuevo) || 0) - (Number(m.stockAnterior) || 0);
  return `${d > 0 ? "+" : d < 0 ? "−" : "±"}${Math.abs(d) || m.cantidad || 0}`;
}

/** En qué número queda el stock, o null si falta algo. */
export function stockResultante(clase, actual, cantidad) {
  const n = Number(cantidad);
  if (String(cantidad ?? "").trim() === "" || !Number.isInteger(n) || n < 0) return null;
  const base = Number(actual) || 0;
  const sentido = CLASES_MANUALES[clase]?.sentido;
  if (sentido === "fija") return n;
  return sentido === "sale" ? base - n : base + n;
}

/** Errores por campo. pieza: la del inventario (o el producto del catálogo en la carga inicial). */
export function erroresMovimientoTaller({ clase, cantidad, costo, motivo }, pieza) {
  const e = {};
  const t = CLASES_MANUALES[clase];
  if (!t) e.clase = "Elige el tipo de movimiento.";
  if (!pieza) e.pieza = clase === "CARGA_INICIAL" ? "Elige la pieza del catálogo." : "Elige la pieza de tu inventario.";
  const n = Number(cantidad);
  if (String(cantidad ?? "").trim() === "") e.cantidad = clase === "AJUSTE" ? "Escribe cuántas contaste." : "Escribe cuántas unidades.";
  else if (!Number.isInteger(n) || n < 0) e.cantidad = "Escribe un número entero, sin puntos ni signos.";
  else if (clase !== "AJUSTE" && n === 0) e.cantidad = "La cantidad debe ser mayor que 0.";
  else if (n > 9999) e.cantidad = "Como máximo 9.999 unidades.";
  else if (t?.sentido === "sale" && pieza && n > (Number(pieza.stock) || 0)) {
    e.cantidad = `No puedes sacar más de lo que tienes: hay ${Number(pieza.stock) || 0}.`;
  } else if (clase === "AJUSTE" && pieza && n === (Number(pieza.stock) || 0)) {
    e.cantidad = "Es el mismo stock que ya tienes: no hay nada que ajustar.";
  }
  if (clase === "CARGA_INICIAL") {
    const c = parsePesos(costo);
    if (String(costo ?? "").trim() === "" || !Number.isFinite(c) || c <= 0) e.costo = "Escribe lo que te costó cada unidad.";
  }
  if (!String(motivo ?? "").trim()) e.motivo = "Cuéntanos el motivo: queda en el historial.";
  return e;
}


/** Piezas con stock bajo: en su mínimo o debajo. */
export const piezasStockBajo = (piezas = []) => piezas.filter((p) => p.stockBajo);
