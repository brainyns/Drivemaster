import ChipEstado from "../ui/ChipEstado";
import BarraProgreso from "../ui/BarraProgreso";
import "../../css/cotizacion.css";

/**
 * Componente que muestra la probabilidad de aprobación predicha por Machine Learning
 * Proyecto de Aula: DriveMaster
 * Autores: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello
 */

// Cada nivel con su tono del sistema (ChipEstado y BarraProgreso): los colores cambian con el tema
const RIESGO = {
  BAJO: { tono: "ok", etiqueta: "Riesgo bajo de rechazo" },
  MEDIO: { tono: "aviso", etiqueta: "Riesgo moderado de rechazo" },
  ALTO: { tono: "error", etiqueta: "Riesgo alto de rechazo" },
};

export default function AlertaPrediccionCotizacion({ prediccion }) {
  if (!prediccion) return null;

  const {
    probabilidadAprobacion,
    probabilidadRechazo,
    nivelRiesgo,
    recomendaciones,
    origen
  } = prediccion;

  const pctAprobacion = Math.round((probabilidadAprobacion || 0) * 100);
  const pctRechazo = Math.round((probabilidadRechazo || 0) * 100);
  const riesgo = RIESGO[nivelRiesgo] || RIESGO.MEDIO;

  return (
    <section className={`apc apc--${riesgo.tono}`} aria-label="Probabilidad estimada de aprobación">
      <div className="apc__cabecera">
        <div>
          <span className="apc__origen">IA predictiva DriveMaster · Proyecto de aula</span>
          <h4 className="apc__titulo">
            Probabilidad estimada de aprobación: <span className="apc__cifra">{pctAprobacion}%</span>
          </h4>
        </div>
        <ChipEstado tono={riesgo.tono}>{riesgo.etiqueta}</ChipEstado>
      </div>

      <BarraProgreso valor={pctAprobacion} tono={riesgo.tono} etiqueta={`Aprobación estimada: ${pctAprobacion}%`} />

      <div className="apc__cifras">
        <span>Aprobación estimada: {pctAprobacion}%</span>
        <span>Riesgo de rechazo: {pctRechazo}%</span>
      </div>

      {/* Recomendaciones generadas por el modelo */}
      {recomendaciones && recomendaciones.length > 0 && (
        <div className="apc__sugerencias">
          <span className="apc__sugerencias-titulo">Sugerencias antes de enviar la cotización:</span>
          <ul>
            {recomendaciones.map((rec, idx) => <li key={idx}>{rec}</li>)}
          </ul>
        </div>
      )}

      {origen && (
        <div className="apc__motor">
          Motor de inferencia: {origen === "PYTHON_API" ? "Servicio Python (FastAPI)" : "Motor embebido (Java Spring)"}
        </div>
      )}
    </section>
  );
}
