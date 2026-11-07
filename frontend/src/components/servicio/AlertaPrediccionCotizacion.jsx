import ChipEstado from "../ui/ChipEstado";
import BarraProgreso from "../ui/BarraProgreso";
import "../../css/cotizacion.css";
import { porcentaje } from "../../utils/formato";

/**
 * Componente que muestra la probabilidad de aprobación predicha por Machine Learning
 * con Explicabilidad Matemática (XAI 2.0).
 * Proyecto de Aula: DriveMaster
 * Autores: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello
 */

const RIESGO = {
  BAJO: { tono: "ok", etiqueta: "Alta probabilidad de cierre" },
  MEDIO: { tono: "aviso", etiqueta: "Probabilidad moderada" },
  ALTO: { tono: "error", etiqueta: "Riesgo alto de rechazo" },
};

export default function AlertaPrediccionCotizacion({ prediccion, estadoReal }) {
  if (!prediccion) return null;

  const {
    probabilidadAprobacion,
    probabilidadRechazo,
    nivelRiesgo,
    factoresFavor,
    factores_favor,
    factoresRiesgo,
    factores_riesgo,
    recomendaciones,
  } = prediccion;

  const listFavor = factoresFavor || factores_favor || [];
  const listRiesgo = factoresRiesgo || factores_riesgo || [];

  // Si la predicción viene con valores en $0 (registro corrupto pendiente de saneamiento), ocultar la tarjeta
  const tieneCeroCorrupto = [...listFavor, ...listRiesgo].some(
    (f) => (f.descripcion || "").includes("$0") || (f.descripcion || "").includes("$ 0")
  );
  if (tieneCeroCorrupto) {
    return null;
  }

  const pctAprobacion = Math.round((probabilidadAprobacion || 0) * 100);
  const pctRechazo = Math.round((probabilidadRechazo || 0) * 100);
  const riesgo = RIESGO[nivelRiesgo] || RIESGO.MEDIO;

  return (
    <section
      className={`apc apc--${riesgo.tono}`}
      aria-label="Probabilidad estimada de aprobación"
    >
      <div className="apc__cabecera">
        <div>
          <span className="apc__origen">IA Predictiva DriveMaster</span>
          <h4 className="apc__titulo">
            Probabilidad estimada de aprobación:{" "}
            <span className="apc__cifra">{porcentaje(pctAprobacion)}</span>
          </h4>
        </div>
        <ChipEstado tono={riesgo.tono}>{riesgo.etiqueta}</ChipEstado>
      </div>

      <BarraProgreso
        valor={pctAprobacion}
        tono={riesgo.tono}
        etiqueta={`Aprobación estimada: ${porcentaje(pctAprobacion)}`}
      />

      <div className="apc__cifras">
        <span>Aprobación estimada: {porcentaje(pctAprobacion)}</span>
        <span>Probabilidad de rechazo: {porcentaje(pctRechazo)}</span>
      </div>

      {/* Auditoría del Modelo: Predicción vs. Resultado Real */}
      {estadoReal && ["APROBADA", "RECHAZADA", "EMITIDA", "VENCIDA"].includes(estadoReal) && (
        <div className={`apc__auditoria apc__auditoria--${(estadoReal || "").toLowerCase()}`}>
          <span className="apc__auditoria-titulo">
            Auditoría de IA: Predicción vs. Resultado Real
          </span>
          <p className="apc__auditoria-texto">
            {estadoReal === "APROBADA" && (
              pctAprobacion >= 50
                ? `El modelo estimó un ${porcentaje(pctAprobacion)} de éxito y el cliente efectivamente APROBÓ la cotización (Acierto predictivo de alta viabilidad comercial).`
                : `El cliente APROBÓ la cotización superando el pronóstico conservador de ${porcentaje(pctAprobacion)} del modelo.`
            )}
            {estadoReal === "RECHAZADA" && (
              pctAprobacion < 50
                ? `El modelo anticipó oportunamente el riesgo (${porcentaje(pctRechazo)} de probabilidad de rechazo) y el cliente efectivamente RECHAZÓ la cotización.`
                : `El cliente RECHAZÓ la cotización pese a la viabilidad proyectada de ${porcentaje(pctAprobacion)}.`
            )}
            {estadoReal === "EMITIDA" && (
              `Cotización vigente: esperando respuesta del cliente con un ${porcentaje(pctAprobacion)} proyectado de aprobación.`
            )}
            {estadoReal === "VENCIDA" && (
              `Cotización vencida por inactividad comercial: el cliente no respondió en el plazo estipulado.`
            )}
          </p>
        </div>
      )}

      {/* Desglose de Factores Matemáticos (Explicabilidad XAI) */}
      {(listFavor.length > 0 || listRiesgo.length > 0) && (
        <div className="apc__xai">
          <span className="apc__xai-titulo">
            Desglose de Factores Clave (Explicabilidad IA):
          </span>
          <div className="apc__factores-grid">
            {listFavor.length > 0 && (
              <div className="apc__columna-factores">
                <span className="apc__columna-header apc__columna-header--favor">
                  ● Factores a Favor
                </span>
                {listFavor.map((f, idx) => (
                  <div
                    key={idx}
                    className="apc__factor-card apc__factor-card--favor"
                  >
                    <div className="apc__factor-top">
                      <span className="apc__factor-titulo">{f.titulo}</span>
                      {f.impacto != null && (
                        <span className="apc__factor-tag apc__factor-tag--favor">
                          +{porcentaje(Math.abs(f.impacto))}
                        </span>
                      )}
                    </div>
                    <span className="apc__factor-desc">{f.descripcion}</span>
                  </div>
                ))}
              </div>
            )}

            {listRiesgo.length > 0 && (
              <div className="apc__columna-factores">
                <span className="apc__columna-header apc__columna-header--riesgo">
                  ▲ Puntos de Riesgo / A Mejorar
                </span>
                {listRiesgo.map((r, idx) => (
                  <div
                    key={idx}
                    className="apc__factor-card apc__factor-card--riesgo"
                  >
                    <div className="apc__factor-top">
                      <span className="apc__factor-titulo">{r.titulo}</span>
                      {r.impacto != null && (
                        <span className="apc__factor-tag apc__factor-tag--riesgo">
                          -{porcentaje(Math.abs(r.impacto))}
                        </span>
                      )}
                    </div>
                    <span className="apc__factor-desc">{r.descripcion}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Recomendaciones dinámicas accionables generadas por el modelo */}
      {recomendaciones && recomendaciones.length > 0 && (
        <div className="apc__sugerencias">
          <span className="apc__sugerencias-titulo">
            Sugerencias para asegurar la orden:
          </span>
          <ul>
            {recomendaciones.map((rec, idx) => (
              <li key={idx}>{rec}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
