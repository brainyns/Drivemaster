// De dónde sale cada repuesto. Una pieza del inventario del taller no tiene producto ni tienda: se identifica por su
// piezaTallerId. La misma pieza puede ir dos veces en una orden (lo del taller y el resto de una tienda), así que la
// clave nunca es solo el producto.

export const esDelTaller = (x) => x?.origen === "TALLER";

/** Identifica una oferta o un repuesto elegido, venga del inventario o de una tienda. */
export const claveOferta = (x) => (esDelTaller(x) ? `taller:${x.piezaTallerId}` : `tienda:${x.productoId}`);

/** La etiqueta que ve el cliente: nunca el costo ni el margen. */
export const etiquetaOrigen = (x) => (esDelTaller(x) ? "Disponible en el taller" : "Se pide a tienda");

/** Quién lo vende, para el taller: "Tu inventario" o el nombre de la tienda. */
export const vendedor = (x) => (esDelTaller(x) ? "Tu inventario" : x?.tiendaNombre);
