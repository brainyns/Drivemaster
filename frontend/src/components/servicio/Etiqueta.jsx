/**
 * Etiqueta de estado con el estilo de EstadoServicio. catalogo: uno de los mapas
 * de estadosServicio.js (ESTADOS_COTIZACION, ESTADOS_PEDIDO…).
 */
export default function Etiqueta({ estado, catalogo }) {
  const info = catalogo[estado] || { label: estado, tono: "info" };
  return (
    <span className={`ss-estado ss-estado--${info.tono}`}>
      <span className="ss-estado__punto" />
      {info.label}
    </span>
  );
}
