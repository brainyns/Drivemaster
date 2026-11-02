import { useEffect, useRef, useState } from "react";
import { textoError } from "../../utils/errores";
import { ayudaMotivo, errorMotivo } from "../../utils/motivo";
import "../../css/marketplace.css";

/**
 * Confirmación antes de una acción que no se deshace (ya lo recibí, reportar un problema, pedir un
 * código nuevo...). Con `pedirMotivo` exige escribir el motivo antes de poder confirmar. `children` agrega
 * contenido bajo el mensaje y `puedeConfirmar={false}` deja el botón desactivado (ej. falta un check).
 * Con minimoMotivo (las cancelaciones usan MINIMO_MOTIVO) el motivo debe tener al menos esos caracteres, con la
 * cuenta bajo el campo. Mientras se envía, el botón también queda desactivado. Escape y el fondo cancelan; el foco entra al diálogo.
 */
export default function ConfirmarAccion({
  titulo, mensaje, textoConfirmar = "Confirmar", peligro = false,
  pedirMotivo = false, etiquetaMotivo = "Motivo", ayudaMotivo: ayuda, minimoMotivo = 0,
  puedeConfirmar = true, children,
  onConfirmar, onCancelar,
}) {
  const [motivo, setMotivo] = useState("");
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState(null);
  const refDialogo = useRef(null);
  const enviando = useRef(false);

  useEffect(() => {
    refDialogo.current?.focus();
    const alTeclear = (e) => { if (e.key === "Escape" && !procesando) onCancelar(); };
    document.addEventListener("keydown", alTeclear);
    return () => document.removeEventListener("keydown", alTeclear);
  }, [onCancelar, procesando]);

  const confirmar = async () => {
    if (enviando.current || !puedeConfirmar) return;
    if (pedirMotivo && !motivo.trim()) {
      setError(`Escribe el ${etiquetaMotivo.toLowerCase()} para continuar.`);
      return;
    }
    const corto = pedirMotivo && minimoMotivo > 0 ? errorMotivo(motivo, minimoMotivo) : null;
    if (corto) {
      setError(corto);
      return;
    }
    enviando.current = true;
    setProcesando(true);
    setError(null);
    try {
      await onConfirmar(pedirMotivo ? motivo.trim() : undefined);
    } catch (e) {
      setError(textoError(e));
      setProcesando(false);
    } finally {
      enviando.current = false;
    }
  };

  return (
    <div className="mk-modal" onClick={() => { if (!procesando) onCancelar(); }}>
      <div className="mk-modal__caja" role="dialog" aria-modal="true" aria-labelledby="mk-modal-titulo"
        tabIndex={-1} ref={refDialogo} onClick={(e) => e.stopPropagation()}>
        <h2 id="mk-modal-titulo">{titulo}</h2>
        {mensaje && <p>{mensaje}</p>}
        {children}
        {pedirMotivo && (
          <div className="mk-campo">
            <label htmlFor="mk-modal-motivo">{etiquetaMotivo}</label>
            <textarea id="mk-modal-motivo" maxLength={500} value={motivo} onChange={(e) => { setMotivo(e.target.value); setError(null); }} />
            {(ayuda || minimoMotivo > 0) && (
              <span className="mk-tarjeta__meta" aria-live="polite">
                {[ayuda, minimoMotivo > 0 && ayudaMotivo(motivo, minimoMotivo)].filter(Boolean).join(" ")}
              </span>
            )}
          </div>
        )}
        {error && <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>}
        <div className="mk-modal__acciones">
          <button className="dm-btn dm-btn--secondary" onClick={onCancelar} disabled={procesando}>Cancelar</button>
          <button className={`dm-btn ${peligro ? "dm-btn--dark" : "dm-btn--primary"}`} onClick={confirmar}
            disabled={procesando || !puedeConfirmar} aria-busy={procesando}>
            {procesando ? "Enviando..." : textoConfirmar}
          </button>
        </div>
      </div>
    </div>
  );
}
