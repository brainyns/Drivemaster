// Tonos de los catálogos de estados (estadosServicio, estadosCompra…) → variante de .ui-badge
const VARIANTE = { ok: "ok", aviso: "aviso", error: "error", info: "info", proceso: "info", apagado: "neutro", acento: "acento" };

/** Estado con texto y un punto de color: el color acompaña, nunca es lo único que informa. */
export default function ChipEstado({ tono = "info", children }) {
  return (
    <span className={`ui-badge ui-badge--${VARIANTE[tono] || "info"}`}>
      <span className="ui-badge__punto" aria-hidden="true" />
      {children}
    </span>
  );
}
