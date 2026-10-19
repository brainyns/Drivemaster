/**
 * Interruptor de dos estados (role="switch"): el texto del estado actual va siempre al lado, así el color nunca
 * informa solo. etiqueta nombra qué se enciende (p. ej. "Publicar Pastillas Brembo"); textos: [encendido, apagado].
 */
export default function Interruptor({ activo, onCambiar, etiqueta, textos = ["Sí", "No"], ocupado = false, disabled = false }) {
  return (
    <button type="button" role="switch" aria-checked={activo} aria-label={etiqueta} aria-busy={ocupado}
      className={`ui-interruptor${activo ? " ui-interruptor--activo" : ""}`} disabled={disabled || ocupado}
      onClick={() => onCambiar(!activo)}>
      <span className="ui-interruptor__pista" aria-hidden="true"><span className="ui-interruptor__perilla" /></span>
      <span className="ui-interruptor__texto" aria-hidden="true">{activo ? textos[0] : textos[1]}</span>
    </button>
  );
}
