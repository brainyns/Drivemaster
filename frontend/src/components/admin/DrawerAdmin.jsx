import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { abrirCapa, manejarTeclas } from "./capaModal";

/**
 * Panel lateral del panel de administración. En móvil ocupa toda la pantalla.
 * Se cierra con Esc, con la X o tocando el fondo.
 */
export default function DrawerAdmin({ titulo, encabezado, pie, onCerrar, children, etiqueta }) {
  const ref = useRef(null);

  useEffect(() => abrirCapa(ref.current), []);

  return createPortal(
    <>
      <div className="adm-velo" onClick={onCerrar} aria-hidden="true" />
      <aside
        ref={ref}
        className="adm-drawer"
        role="dialog"
        aria-modal="true"
        aria-label={etiqueta || titulo}
        tabIndex={-1}
        onKeyDown={(e) => manejarTeclas(e, ref.current, onCerrar)}
      >
        <header className="adm-drawer__cabecera">
          <div>
            {encabezado}
            <h2 className="adm-drawer__titulo">{titulo}</h2>
          </div>
          <button type="button" className="adm-drawer__cerrar" onClick={onCerrar} aria-label="Cerrar">×</button>
        </header>
        <div className="adm-drawer__cuerpo">{children}</div>
        {pie && <footer className="adm-drawer__pie">{pie}</footer>}
      </aside>
    </>,
    document.body
  );
}
