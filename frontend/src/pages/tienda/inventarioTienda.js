// Inventario de la tienda: tipos de movimiento, filtros, resumen y el formulario de movimiento manual. Sin
// componentes: lo prueban los tests con node --test. Las reglas son las del backend (MovimientoInventarioServiceImpl):
// ENTRADA suma, SALIDA resta y AJUSTE fija el stock al número contado.

export const TIPOS = {
  ENTRADA: { label: "Entrada", plural: "Entradas", tono: "ok", signo: "+", campo: "Unidades que entran",
    ayuda: "Llegó mercancía: se suma al stock." },
  SALIDA: { label: "Salida", plural: "Salidas", tono: "error", signo: "−", campo: "Unidades que salen",
    ayuda: "Salió sin pasar por un pedido (daño, uso interno): se resta del stock." },
  AJUSTE: { label: "Ajuste", plural: "Ajustes", tono: "aviso", signo: "", campo: "Stock contado",
    ayuda: "Conteo físico: el stock queda exactamente en este número." },
};

export const FILTROS_TIPO = [
  { key: "", label: "Todos" },
  ...Object.entries(TIPOS).map(([key, t]) => ({ key, label: t.plural })),
];

/** Movimientos que cumplen el filtro: producto (nombre o código), tipo y rango de fechas (ambos incluidos). */
export function filtrarMovimientos(movimientos, { busqueda = "", tipo = "", desde = "", hasta = "" }, producto) {
  const q = busqueda.trim().toLowerCase();
  const inicio = desde ? new Date(`${desde}T00:00:00`) : null;
  const fin = hasta ? new Date(`${hasta}T23:59:59`) : null;
  return movimientos.filter((m) => {
    const p = producto(m.productoId);
    if (q && ![p?.nombre, p?.codigo].some((x) => x && String(x).toLowerCase().includes(q))) return false;
    if (tipo && m.tipo !== tipo) return false;
    const f = m.fecha ? new Date(m.fecha) : null;
    if (f && inicio && f < inicio) return false;
    if (f && fin && f > fin) return false;
    return true;
  }).sort((a, b) => new Date(b.fecha || 0) - new Date(a.fecha || 0));
}

export function resumenMovimientos(movimientos) {
  const suma = (tipo) => movimientos.filter((m) => m.tipo === tipo).reduce((s, m) => s + (Number(m.cantidad) || 0), 0);
  return {
    total: movimientos.length,
    entradas: suma("ENTRADA"),
    salidas: suma("SALIDA"),
    ajustes: movimientos.filter((m) => m.tipo === "AJUSTE").length,
  };
}

/** En qué número queda el stock con este movimiento, o null si falta algo. */
export function stockResultante(tipo, actual, cantidad) {
  const n = Number(cantidad);
  if (String(cantidad ?? "").trim() === "" || !Number.isInteger(n) || n < 0) return null;
  const base = Number(actual) || 0;
  if (tipo === "ENTRADA") return base + n;
  if (tipo === "SALIDA") return base - n;
  return n;
}

/** Errores del movimiento manual, por campo. */
export function erroresMovimiento({ productoId, tipo, cantidad, motivo }, stockActual) {
  const e = {};
  if (!productoId) e.productoId = "Elige el producto.";
  const n = Number(cantidad);
  if (String(cantidad ?? "").trim() === "") e.cantidad = tipo === "AJUSTE" ? "Escribe cuántas unidades contaste." : "Escribe cuántas unidades.";
  else if (!Number.isInteger(n) || n < 0) e.cantidad = "Escribe un número entero, sin puntos ni signos.";
  else if (tipo !== "AJUSTE" && n === 0) e.cantidad = "La cantidad debe ser mayor que 0.";
  else if (tipo === "SALIDA" && productoId && n > (Number(stockActual) || 0)) {
    e.cantidad = `No puedes sacar más de lo que hay: el stock actual es ${Number(stockActual) || 0}.`;
  }
  if (!String(motivo ?? "").trim()) e.motivo = "Cuéntanos el motivo: queda en el historial.";
  return e;
}
