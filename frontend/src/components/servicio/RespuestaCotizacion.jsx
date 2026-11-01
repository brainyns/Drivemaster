import { useState } from "react";

const OPCIONES = [
  { accion: "APROBAR",  label: "Aprobar" },
  { accion: "CAMBIOS",  label: "Pedir cambios" },
  { accion: "RECHAZAR", label: "Rechazar" },
];

const CONFIRMAR = {
  APROBAR:  "Confirmar aprobación",
  CAMBIOS:  "Enviar al taller",
  RECHAZAR: "Confirmar rechazo",
};

const AYUDA = {
  APROBAR:  "Después de aprobar podrás pagar en línea. El pago confirma el pedido de repuestos.",
  CAMBIOS:  "El taller revisará tu comentario y te enviará una nueva cotización.",
  RECHAZAR: "La solicitud se cierra. El diagnóstico ya pagado no se reembolsa.",
};

const AYUDA_SIN_SALDO = "El diagnóstico ya cubre esta cotización: no tienes nada por pagar. Al aprobar, el taller empieza el trabajo.";

/**
 * Aprobar, pedir cambios o rechazar una cotización. onResponder(accion, motivo)
 * hace la llamada; accionInicial preselecciona la opción (enlace del correo).
 * sinSaldo: el diagnóstico cubrió todo y aprobar no lleva a ningún pago.
 */
export default function RespuestaCotizacion({ onResponder, accionInicial = null, sinSaldo = false }) {
  const [accion, setAccion] = useState(accionInicial);
  const [motivo, setMotivo] = useState("");
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  const enviar = async () => {
    if (accion === "CAMBIOS" && !motivo.trim()) return setError("Cuéntale al taller qué quieres cambiar.");
    setEnviando(true);
    setError(null);
    try {
      await onResponder(accion, motivo.trim() || null);
    } catch (e) {
      setError(e.message);
      setEnviando(false);
    }
  };

  return (
    <div className="ct-respuesta">
      <div className="ct-respuesta__opciones" role="group" aria-label="Tu respuesta">
        {OPCIONES.map((o) => (
          <button key={o.accion} type="button" disabled={enviando}
            className={`sv-btn-outline${accion === o.accion ? " ct-opcion--activa" : ""}`}
            aria-pressed={accion === o.accion}
            onClick={() => { setAccion(o.accion); setError(null); }}>
            {o.label}
          </button>
        ))}
      </div>

      {accion && (
        <>
          <p className="ss-meta">{accion === "APROBAR" && sinSaldo ? AYUDA_SIN_SALDO : AYUDA[accion]}</p>
          {accion !== "APROBAR" && (
            <>
              <label className="ss-meta" htmlFor="ct-motivo">
                {accion === "CAMBIOS" ? "¿Qué quieres cambiar?" : "Motivo (opcional)"}
              </label>
              <textarea id="ct-motivo" className="ss-textarea" rows={3} maxLength={1000}
                value={motivo} onChange={(e) => setMotivo(e.target.value)} />
            </>
          )}
          {error && <p className="ss-alerta" role="alert">{error}</p>}
          <div className="ss-acciones">
            <button type="button" className={`sv-btn${accion === "RECHAZAR" ? " ss-btn-peligro" : ""}`}
              onClick={enviar} disabled={enviando}>
              {enviando ? "Enviando..." : CONFIRMAR[accion]}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
