// Compras a proveedores de la tienda: referencia, filtros, resumen y validación de una compra nueva. Sin componentes:
// lo prueban los tests con node --test. Al registrar una compra el backend suma el stock (movimiento ENTRADA) y, si
// el proveedor tiene correo, le envía el PDF; una compra no tiene estados (queda registrada al guardarla).

/** La misma referencia que usa el PDF del backend: PO- y los últimos 5 caracteres del id. */
export const refCompra = (id) => `PO-${String(id || "").slice(-5).toUpperCase()}`;

export const unidades = (c) => (c.productos || []).reduce((s, p) => s + (Number(p.cantidad) || 0), 0);

/** Compras que cumplen búsqueda (referencia, proveedor o producto), fechas y rango de total; las más recientes primero. */
export function filtrarCompras(compras, { busqueda = "", desde = "", hasta = "", minimo = "", maximo = "" }, nombreProveedor) {
  const q = busqueda.trim().toLowerCase();
  const inicio = desde ? new Date(`${desde}T00:00:00`) : null;
  const fin = hasta ? new Date(`${hasta}T23:59:59`) : null;
  return compras.filter((c) => {
    if (q && ![refCompra(c.id), c.id, nombreProveedor(c.proveedorId), ...(c.productos || []).map((p) => p.nombre)]
      .some((x) => x && String(x).toLowerCase().includes(q))) return false;
    const f = c.fecha ? new Date(c.fecha) : null;
    if (f && inicio && f < inicio) return false;
    if (f && fin && f > fin) return false;
    if (minimo !== "" && (c.total || 0) < Number(minimo)) return false;
    if (maximo !== "" && (c.total || 0) > Number(maximo)) return false;
    return true;
  }).sort((a, b) => new Date(b.fecha || 0) - new Date(a.fecha || 0));
}

/** Total gastado, unidades compradas y lo comprado en el mes de `ahora`. */
export function resumenCompras(compras, ahora = Date.now()) {
  const hoy = new Date(ahora);
  const delMes = compras.filter((c) => {
    const f = c.fecha ? new Date(c.fecha) : null;
    return f && f.getFullYear() === hoy.getFullYear() && f.getMonth() === hoy.getMonth();
  });
  return {
    total: compras.length,
    gastado: compras.reduce((s, c) => s + (Number(c.total) || 0), 0),
    unidades: compras.reduce((s, c) => s + unidades(c), 0),
    mes: delMes.reduce((s, c) => s + (Number(c.total) || 0), 0),
    comprasMes: delMes.length,
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
