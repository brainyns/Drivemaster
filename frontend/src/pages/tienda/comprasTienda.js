// Compras a proveedores de la tienda: referencia, estados, filtros, resumen y validación de una compra nueva. Sin
// componentes: lo prueban los tests con node --test. Las reglas son las del backend (CompraServiceImpl): una compra
// RECIBIDA suma el stock (movimiento ENTRADA); una PENDIENTE lo suma al marcarla recibida; una CANCELADA no lo toca.
// Sin estado = RECIBIDA (las compras anteriores a los estados sumaron el stock al registrarse).

/** La misma referencia que usa el PDF del backend: PO- y los últimos 5 caracteres del id. */
export const refCompra = (id) => `PO-${String(id || "").slice(-5).toUpperCase()}`;

export const unidades = (c) => (c.productos || []).reduce((s, p) => s + (Number(p.cantidad) || 0), 0);

export const ESTADOS_COMPRA = {
  RECIBIDA: { label: "Recibida", tono: "ok" },
  PENDIENTE: { label: "Pendiente", tono: "aviso" },
  CANCELADA: { label: "Cancelada", tono: "apagado" },
};

export const estadoCompra = (c) => c?.estado || "RECIBIDA";
export const cuentaEnGasto = (c) => estadoCompra(c) !== "CANCELADA";

export const METODOS_PAGO = [
  ["EFECTIVO", "Efectivo"], ["TRANSFERENCIA", "Transferencia"], ["TARJETA", "Tarjeta"], ["CREDITO", "Crédito del proveedor"],
];
export const etiquetaMetodoPago = (m) => (METODOS_PAGO.find(([v]) => v === m) || [null, null])[1];

/** Variación en % entre dos montos, con un decimal; null si el mes anterior no tuvo gasto (no hay contra qué comparar). */
export function variacion(actual, anterior) {
  if (!anterior) return null;
  return Math.round(((actual - anterior) / anterior) * 1000) / 10;
}

/**
 * Los proveedores a los que más se les compró (por monto, sin las canceladas), con cuántas compras y la última.
 * nombreProveedor resuelve el id; los que ya no existen se agrupan como "Proveedor eliminado".
 */
export function topProveedores(compras, nombreProveedor, max = 3) {
  const porProveedor = new Map();
  compras.filter(cuentaEnGasto).forEach((c) => {
    const actual = porProveedor.get(c.proveedorId) || { proveedorId: c.proveedorId, total: 0, compras: 0, ultima: null };
    actual.total += Number(c.total) || 0;
    actual.compras += 1;
    if (c.fecha && (!actual.ultima || new Date(c.fecha) > new Date(actual.ultima))) actual.ultima = c.fecha;
    porProveedor.set(c.proveedorId, actual);
  });
  return [...porProveedor.values()]
    .map((p) => ({ ...p, nombre: nombreProveedor(p.proveedorId) || "Proveedor eliminado" }))
    .sort((a, b) => b.total - a.total)
    .slice(0, max);
}

/** Lo comprado a un proveedor: sus compras (más recientes primero), el total sin canceladas y la fecha de la última. */
export function comprasDeProveedor(compras, proveedorId) {
  const suyas = compras.filter((c) => c.proveedorId === proveedorId)
    .sort((a, b) => new Date(b.fecha || 0) - new Date(a.fecha || 0));
  const validas = suyas.filter(cuentaEnGasto);
  return {
    compras: suyas,
    total: validas.reduce((s, c) => s + (Number(c.total) || 0), 0),
    ultima: validas[0]?.fecha || null,
    pendientes: suyas.filter((c) => estadoCompra(c) === "PENDIENTE").length,
  };
}

/** Compras que cumplen búsqueda (referencia, proveedor o producto), fechas y rango de total; las más recientes primero. */
export function filtrarCompras(compras, { busqueda = "", desde = "", hasta = "", minimo = "", maximo = "", estado = "" }, nombreProveedor) {
  const q = busqueda.trim().toLowerCase();
  const inicio = desde ? new Date(`${desde}T00:00:00`) : null;
  const fin = hasta ? new Date(`${hasta}T23:59:59`) : null;
  return compras.filter((c) => {
    if (estado && estadoCompra(c) !== estado) return false;
    if (q && ![refCompra(c.id), c.id, c.numeroFactura, nombreProveedor(c.proveedorId), ...(c.productos || []).map((p) => p.nombre)]
      .some((x) => x && String(x).toLowerCase().includes(q))) return false;
    const f = c.fecha ? new Date(c.fecha) : null;
    if (f && inicio && f < inicio) return false;
    if (f && fin && f > fin) return false;
    if (minimo !== "" && (c.total || 0) < Number(minimo)) return false;
    if (maximo !== "" && (c.total || 0) > Number(maximo)) return false;
    return true;
  }).sort((a, b) => new Date(b.fecha || 0) - new Date(a.fecha || 0));
}

/**
 * Total gastado, unidades recibidas, lo comprado en el mes de `ahora` y en el anterior (con la variación en %) y
 * cuántas siguen pendientes. Las canceladas no cuentan como gasto; las pendientes sí (ya están comprometidas), pero
 * sus unidades no, porque aún no llegaron.
 */
export function resumenCompras(compras, ahora = Date.now()) {
  const hoy = new Date(ahora);
  const anterior = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1);
  const delMes = (anio, mes) => compras.filter((c) => {
    const f = c.fecha ? new Date(c.fecha) : null;
    return cuentaEnGasto(c) && f && f.getFullYear() === anio && f.getMonth() === mes;
  });
  const suma = (lista) => lista.reduce((s, c) => s + (Number(c.total) || 0), 0);
  const esteMes = delMes(hoy.getFullYear(), hoy.getMonth());
  const mesAnterior = suma(delMes(anterior.getFullYear(), anterior.getMonth()));
  const mes = suma(esteMes);
  return {
    total: compras.length,
    gastado: suma(compras.filter(cuentaEnGasto)),
    unidades: compras.filter((c) => estadoCompra(c) === "RECIBIDA").reduce((s, c) => s + unidades(c), 0),
    mes,
    comprasMes: esteMes.length,
    mesAnterior,
    variacionMes: variacion(mes, mesAnterior),
    pendientes: compras.filter((c) => estadoCompra(c) === "PENDIENTE").length,
  };
}

/** Errores de la línea que se va a agregar (producto, cantidad y costo). */
export function erroresLinea({ producto, cantidad, costo }) {
  const e = {};
  if (!producto) e.producto = "Busca y elige un producto de tu catálogo.";
  const n = Number(cantidad);
  if (String(cantidad ?? "").trim() === "" || !Number.isInteger(n) || n < 1) e.cantidad = "Escribe una cantidad entera desde 1.";
  const c = Number(costo);
  if (String(costo ?? "").trim() === "" || !Number.isFinite(c) || c < 0) e.costo = "Escribe el costo por unidad (puede ser 0).";
  return e;
}

/** Errores de la compra completa antes de confirmarla. */
export function erroresCompra({ proveedorId, items }) {
  const e = {};
  if (!proveedorId) e.proveedor = "Elige el proveedor de esta compra.";
  if (!items.length) e.items = "Agrega al menos un producto.";
  return e;
}

/** Agrega una línea; si el producto ya está, suma la cantidad y conserva su costo. */
export function agregarLinea(items, { producto, cantidad, costo }) {
  const n = Number(cantidad);
  const existe = items.find((i) => i.productoId === producto.id);
  if (existe) {
    return items.map((i) => (i.productoId === producto.id
      ? { ...i, cantidad: i.cantidad + n, subtotal: (i.cantidad + n) * i.costo } : i));
  }
  return [...items, {
    productoId: producto.id, nombre: producto.nombre, imagenUrl: producto.imagenUrl,
    sku: producto.codigo || String(producto.id).slice(-6).toUpperCase(),
    costo: Number(costo), cantidad: n, subtotal: Number(costo) * n,
  }];
}
