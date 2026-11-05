import { useEffect, useRef, useState } from "react";
import "../../css/info-ayuda.css";

/**
 * Ayuda de una línea: un botón "?" que, al tocarlo, muestra la frase. Para que nadie tenga que adivinar un
 * término del dinero. Se cierra con Esc, con el puntero fuera o volviendo a tocar el botón.
 */
export default function InfoAyuda({ texto, etiqueta = "¿Qué significa?" }) {
  const [abierto, setAbierto] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!abierto) return undefined;
    const alClic = (e) => { if (!ref.current?.contains(e.target)) setAbierto(false); };
    const alEsc = (e) => { if (e.key === "Escape") setAbierto(false); };
    document.addEventListener("pointerdown", alClic);
    document.addEventListener("keydown", alEsc);
    return () => { document.removeEventListener("pointerdown", alClic); document.removeEventListener("keydown", alEsc); };
  }, [abierto]);

  return (
    <span className="ia-root" ref={ref}>
      <button type="button" className="ia-boton" aria-expanded={abierto} aria-label={`${etiqueta}: ${texto}`}
        onClick={() => setAbierto((v) => !v)}>
        ?
      </button>
      {abierto && <span role="tooltip" className="ia-globo">{texto}</span>}
    </span>
  );
}
