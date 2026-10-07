import { ESTADOS } from "./estadosServicio";

/** Etiqueta de estado de una solicitud del marketplace. */
export default function EstadoServicio({ estado }) {
  const info = ESTADOS[estado] || { label: estado, tono: "info" };
  return (
    <span className={`ss-estado ss-estado--${info.tono}`}>
      <span className="ss-estado__punto" />
      {info.label}
    </span>
  );
}
