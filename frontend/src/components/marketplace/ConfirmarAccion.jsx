import { useEffect, useRef, useState } from "react";

/**
 * Confirmación antes de una acción que no se deshace (ya lo recibí, reportar un problema, pedir un
 * código nuevo...). Con `pedirMotivo` exige escribir el motivo antes de poder confirmar. Escape y el
 * fondo cancelan; el foco entra al diálogo.
 */
export default function ConfirmarAccion({
  titulo, mensaje, textoConfirmar = "Confirmar", peligro = false,
  pedirMotivo = false, etiquetaMotivo = "Motivo", ayudaMotivo,
  onConfirmar, onCancelar,
}) {
  const [motivo, setMotivo] = useState("");
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState(null);
  const refDialogo = useRef(null);

  useEffect(() => {
    refDialogo.current?.focus();
    const alTeclear = (e) => { if (e.key === "Escape" && !procesando) onCancelar(); };
    document.addEventListener("keydown", alTeclear);
    return () => document.removeEventListener("keydown", alTeclear);
  }, [onCancelar, procesando]);

  const confirmar = async () => {
    if (pedirMotivo && !motivo.trim()) {
      setError(`Escribe el ${etiquetaMotivo.toLowerCase()} para continuar.`);
      return;
    }
    setProcesando(true);
    setError(null);
    try {
      await onConfirmar(pedirMotivo ? motivo.trim() : undefined);
    } catch (e) {
      setError(e.message);
      setProcesando(false);
    }
  };

  return (
    <div className="mk-modal" onClick={() => { if (!procesando) onCancelar(); }}>
      <div className="mk-modal__caja" role="dialog" aria-modal="true" aria-labelledby="mk-modal-titulo"
        tabIndex={-1} ref={refDialogo} onClick={(e) => e.stopPropagation()}>
        <h2 id="mk-modal-titulo">{titulo}</h2>
        <p>{mensaje}</p>
        {pedirMotivo && (
          <div className="mk-campo">
            <label htmlFor="mk-modal-motivo">{etiquetaMotivo}</label>
            <textarea id="mk-modal-motivo" maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
            {ayudaMotivo && <span className="mk-tarjeta__meta">{ayudaMotivo}</span>}
          </div>
        )}
        {error && <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>}
        <div className="mk-modal__acciones">
          <button className="dm-btn dm-btn--secondary" onClick={onCancelar} disabled={procesando}>Cancelar</button>
          <button className={`dm-btn ${peligro ? "dm-btn--dark" : "dm-btn--primary"}`} onClick={confirmar} disabled={procesando}>
            {procesando ? "Enviando..." : textoConfirmar}
          </button>
        </div>
      </div>
    </div>
  );
}
