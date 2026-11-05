// Tonos de los catálogos de estados (estadosServicio, estadosCompra…) → variante de .ui-badge
const VARIANTE = {
  ok: "ok", aviso: "aviso", error: "error", info: "info", proceso: "info", apagado: "neutro", acento: "acento",
  // Fases del panel del taller (estadosTaller.js)
  nueva: "fase-nueva", cotizacion: "fase-cotizacion", curso: "fase-curso",
  lista: "fase-lista", entregada: "fase-entregada", cancelada: "fase-cancelada",
};

/**
 * Estado con texto y un punto de color: el color acompaña, nunca es lo único que informa. Con catalogo y estado
 * (p. ej. ESTADOS_COTIZACION y "EMITIDA") toma el texto y el tono del catálogo; children se añade al final.
 */
export default function ChipEstado({ tono = "info", estado, catalogo, children }) {
  const info = catalogo ? catalogo[estado] || { label: estado, tono: "info" } : null;
  return (
    <span className={`ui-badge ui-badge--${VARIANTE[info ? info.tono : tono] || "info"}`}>
      <span className="ui-badge__punto" aria-hidden="true" />
      {info?.label}
      {children}
    </span>
  );
}
