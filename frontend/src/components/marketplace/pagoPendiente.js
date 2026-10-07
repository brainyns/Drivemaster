// Qué productos del carrito se estaban pagando, para vaciarlos cuando Wompi confirme (el navegador sale
// a Wompi y vuelve): solo ids de productos, nunca datos de pago. Archivo sin componentes.

const CLAVE = "mk.pagoPendiente";

export function guardarPagoPendiente(compraId, ids) {
  try { localStorage.setItem(CLAVE, JSON.stringify({ compraId, ids })); } catch { /* sin almacenamiento: se limpia a mano */ }
}

/** Devuelve { compraId, ids } y lo borra, o null. */
export function consumirPagoPendiente() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE));
    localStorage.removeItem(CLAVE);
    return guardado && Array.isArray(guardado.ids) ? guardado : null;
  } catch {
    return null;
  }
}
