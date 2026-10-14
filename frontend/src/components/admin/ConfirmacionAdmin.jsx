import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { abrirCapa, manejarTeclas } from "./capaModal";

/**
 * Diálogo para confirmar una acción (aprobar, rechazar, desactivar...).
 * variante: "primario" | "peligro". children: campos extra (plan, motivo).
 */
export default function ConfirmacionAdmin({
  titulo, texto, children, confirmar, variante = "primario",
  cargando = false, deshabilitado = false, onConfirmar, onCancelar,
}) {
  const ref = useRef(null);

  useEffect(() => abrirCapa(ref.current), []);

  const cancelar = () => { if (!cargando) onCancelar(); };

  return createPortal(
    <>
      <div className="adm-velo adm-velo--dialogo" onClick={cancelar} aria-hidden="true" />
      <form
        ref={ref}
        className="adm-dialogo"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="adm-dialogo-titulo"
        tabIndex={-1}
        onKeyDown={(e) => manejarTeclas(e, ref.current, cancelar)}
        onSubmit={(e) => { e.preventDefault(); if (!cargando && !deshabilitado) onConfirmar(); }}
      >
        <h2 id="adm-dialogo-titulo" className="adm-dialogo__titulo">{titulo}</h2>
        {texto && <p className="adm-dialogo__texto">{texto}</p>}
        {children}
        <div className="adm-dialogo__acciones">
          <button type="button" className="adm-btn" onClick={cancelar} disabled={cargando}>Cancelar</button>
          <button
            type="submit"
            className={`adm-btn adm-btn--${variante}${cargando ? " adm-btn--cargando" : ""}`}
            disabled={cargando || deshabilitado}
            aria-busy={cargando}
          >
            {cargando ? "Procesando…" : confirmar}
          </button>
        </div>
      </form>
    </>,
    document.body
  );
}
