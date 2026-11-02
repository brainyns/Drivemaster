// Lo que paga quien mira el catálogo: un taller aprobado, el precio con el descuento de esa tienda; los demás, el público.
// El backend manda precioTaller solo a un taller aprobado y vuelve a calcularlo al cobrar: aquí solo se muestra.
export const precioQuePaga = (p) => (p?.precioTaller != null ? p.precioTaller : p?.precio);

/** "−20 %", "−12,5 %". */
export const textoDescuento = (pct) => `−${String(pct).replace(".", ",")} %`;
