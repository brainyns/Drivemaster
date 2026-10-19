import { PASOS_SERVICIO } from "./solicitudDiagnostico";

/**
 * Indicador de los cinco pasos del servicio (diagnóstico → instalación) con el paso actual resaltado.
 * actual: número de paso, desde 1. En celular solo se ven las marcas y el nombre del paso actual.
 */
export default function PasosServicio({ actual = 1 }) {
  const total = PASOS_SERVICIO.length;
  return (
    <div className="sd-pasos">
      <p className="sd-pasos__texto" id="sd-pasos-texto">
        Paso {actual} de {total}: <strong>{PASOS_SERVICIO[actual - 1]}</strong>
      </p>
      <ol className="sd-pasos__lista" aria-labelledby="sd-pasos-texto">
        {PASOS_SERVICIO.map((nombre, i) => {
          const n = i + 1;
          const estado = n < actual ? "hecho" : n === actual ? "actual" : "pendiente";
          return (
            <li key={nombre} className={`sd-pasos__paso sd-pasos__paso--${estado}`} aria-current={n === actual ? "step" : undefined}>
              <span className="sd-pasos__marca" aria-hidden="true">{n}</span>
              <span className="sd-pasos__nombre">{nombre}</span>
              <span className="sd-sr">{estado === "hecho" ? " (completado)" : estado === "actual" ? " (paso actual)" : ""}</span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
