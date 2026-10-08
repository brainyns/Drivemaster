/**
 * Etiquetas de un producto del catálogo marketplace. La compatibilidad solo avisa: un producto
 * "No compatible" se muestra igual (y se puede comprar), pero con el aviso a la vista.
 */
const COMPATIBILIDAD = {
  COMPATIBLE: { texto: "Compatible con tu vehículo", tono: "ok" },
  NO_COMPATIBLE: { texto: "No compatible con tu vehículo", tono: "error" },
  POR_CONFIRMAR: { texto: "Confirma la compatibilidad", tono: "aviso" },
};

export function EtiquetaCompatibilidad({ producto }) {
  const c = COMPATIBILIDAD[producto.compatibilidad];
  if (!c) return null;
  return <span className={`mk-etiqueta mk-etiqueta--${c.tono}`}>{c.texto}</span>;
}

/** Aviso largo, para el detalle y el carrito. */
export function AvisoCompatibilidad({ producto }) {
  if (!producto.avisoCompatibilidad) return null;
  const tono = producto.compatibilidad === "NO_COMPATIBLE" ? "error" : "aviso";
  return <p className={`mk-aviso mk-aviso--${tono}`} role="note">{producto.avisoCompatibilidad}</p>;
}

export function EtiquetaVendedor({ producto }) {
  return <span className="mk-etiqueta">{producto.tienda?.nombre}</span>;
}

