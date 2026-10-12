import React from "react";
import "../../css/cotizacion.css";

/**
 * Componente que muestra la probabilidad de aprobación predicha por Machine Learning
 * Proyecto de Aula: DriveMaster
 * Autores: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello
 */
export default function AlertaPrediccionCotizacion({ prediccion }) {
  if (!prediccion) return null;

  const {
    probabilidadAprobacion,
    probabilidadRechazo,
    nivelRiesgo,
    prediccion: resultadoTexto,
    recomendaciones,
    origen
  } = prediccion;

  const pctAprobacion = Math.round((probabilidadAprobacion || 0) * 100);
  const pctRechazo = Math.round((probabilidadRechazo || 0) * 100);

  // Configuración de estilo según nivel de riesgo
  const estilosRiesgo = {
    BAJO: {
      colorBorde: "#10b981",
      bgFondo: "rgba(16, 185, 129, 0.08)",
      colorTexto: "#059669",
      etiqueta: "Riesgo Bajo de Rechazo",
      badgeClass: "badge-riesgo-bajo"
    },
    MEDIO: {
      colorBorde: "#f59e0b",
      bgFondo: "rgba(245, 158, 11, 0.08)",
      colorTexto: "#d97706",
      etiqueta: "Riesgo Moderado de Rechazo",
      badgeClass: "badge-riesgo-medio"
    },
    ALTO: {
      colorBorde: "#ef4444",
      bgFondo: "rgba(239, 68, 68, 0.08)",
      colorTexto: "#dc2626",
      etiqueta: "Riesgo Alto de Rechazo",
      badgeClass: "badge-riesgo-alto"
    }
  };

  const estilo = estilosRiesgo[nivelRiesgo] || estilosRiesgo.MEDIO;

  return (
    <div
      style={{
        margin: "18px 0",
        padding: "16px 20px",
        borderRadius: "10px",
        border: `1.5px solid ${estilo.colorBorde}`,
        backgroundColor: estilo.bgFondo,
        transition: "all 0.3s ease"
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "10px" }}>
        <div>
          <span style={{ fontSize: "11px", textTransform: "uppercase", fontWeight: "700", letterSpacing: "0.5px", color: estilo.colorTexto }}>
            IA Predictiva DriveMaster · Proyecto de Aula
          </span>
          <h4 style={{ margin: "4px 0", fontSize: "16px", fontWeight: "600", color: "#111827" }}>
            Probabilidad estimada de aprobación: <span style={{ color: estilo.colorTexto, fontWeight: "700" }}>{pctAprobacion}%</span>
          </h4>
        </div>
        <div
          style={{
            padding: "4px 12px",
            borderRadius: "20px",
            backgroundColor: estilo.colorBorde,
            color: "#ffffff",
            fontSize: "12px",
            fontWeight: "700",
            textTransform: "uppercase"
          }}
        >
          {estilo.etiqueta}
        </div>
      </div>

      {/* Barra de progreso visual */}
      <div style={{ margin: "12px 0 8px 0", height: "8px", width: "100%", backgroundColor: "#e5e7eb", borderRadius: "4px", overflow: "hidden" }}>
        <div
          style={{
            height: "100%",
            width: `${pctAprobacion}%`,
            backgroundColor: estilo.colorBorde,
            borderRadius: "4px",
            transition: "width 0.6s ease-in-out"
          }}
        />
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", fontSize: "12px", color: "#6b7280" }}>
        <span>Aprobación estimada: {pctAprobacion}%</span>
        <span>Riesgo de rechazo: {pctRechazo}%</span>
      </div>

      {/* Recomendaciones generadas por el modelo */}
      {recomendaciones && recomendaciones.length > 0 && (
        <div style={{ marginTop: "12px", paddingTop: "10px", borderTop: "1px dashed rgba(0,0,0,0.1)" }}>
          <span style={{ fontSize: "12px", fontWeight: "600", color: "#374151" }}>
            Sugerencias antes de enviar la cotización:
          </span>
          <ul style={{ margin: "6px 0 0 16px", padding: 0, fontSize: "12px", color: "#4b5563", lineHeight: "1.5" }}>
            {recomendaciones.map((rec, idx) => (
              <li key={idx} style={{ marginBottom: "3px" }}>{rec}</li>
            ))}
          </ul>
        </div>
      )}

      {origen && (
        <div style={{ marginTop: "8px", textAlign: "right", fontSize: "10px", color: "#9ca3af" }}>
          Motor de inferencia: {origen === "PYTHON_API" ? "Servicio Python (FastAPI)" : "Motor Embebido (Java Spring)"}
        </div>
      )}
    </div>
  );
}

