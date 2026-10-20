import { useState } from "react";
import { confirmarRecepcion } from "../../services/solicitudServicioService";
import { formatFechaHora } from "./estadosServicio";
import Icon from "../public/Icon";

/**
 * El cliente confirma que recibió el vehículo. La calificación al taller es
 * opcional: se puede confirmar sin calificar.
 */
export default function ConfirmarRecepcion({ token, solicitud, onConfirmada }) {
  const [calificacion, setCalificacion] = useState(0);
  const [comentario, setComentario] = useState("");
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  if (solicitud.recepcionConfirmada) {
    return (
      <section className="ss-bloque">
        <h2 className="ss-bloque__titulo">Recepción confirmada</h2>
        <p className="ss-meta">
          Confirmaste la recepción el {formatFechaHora(solicitud.fechaRecepcionCliente)}
          {solicitud.calificacionTaller ? ` · Calificaste al taller con ${solicitud.calificacionTaller}/5` : ""}
        </p>
        {solicitud.comentarioCalificacion && <p className="ss-texto">{solicitud.comentarioCalificacion}</p>}
      </section>
    );
  }

  const enviar = async () => {
    setEnviando(true);
    setError(null);
    try {
      await confirmarRecepcion(token, solicitud.id, calificacion || null, comentario.trim() || null);
      await onConfirmada();
    } catch (e) {
      setError(e.message);
      setEnviando(false);
    }
  };

  return (
    <section className="ss-bloque ss-bloque--destacado">
      <h2 className="ss-bloque__titulo">¿Ya recibiste tu vehículo?</h2>
      <p className="ss-meta">
        El taller marcó la entrega el {formatFechaHora(solicitud.fechaEntrega)}. Confírmalo y, si quieres, califica el servicio.
      </p>
      <div className="ct-calificacion" role="radiogroup" aria-label="Calificación del taller (opcional)">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={calificacion === n}
            aria-label={`${n} de 5`} className={`ct-estrella${n <= calificacion ? " ct-estrella--activa" : ""}`}
            onClick={() => setCalificacion(calificacion === n ? 0 : n)} disabled={enviando}>
            <Icon name="star" size={24} />
          </button>
        ))}
      </div>
      <label className="ss-meta" htmlFor="ct-comentario">Comentario (opcional)</label>
      <textarea id="ct-comentario" className="ss-textarea" rows={2} maxLength={500}
        value={comentario} onChange={(e) => setComentario(e.target.value)} />
      {error && <p className="ss-alerta" role="alert">{error}</p>}
      <div className="ss-acciones">
        <button className="sv-btn" onClick={enviar} disabled={enviando}>
          {enviando ? "Confirmando..." : "Confirmar recepción"}
        </button>
        <span className="ss-ayuda">La calificación es opcional.</span>
      </div>
    </section>
  );
}
