import { useState } from "react";
import Icon from "../public/Icon";

/**
 * Contraseña con botón para mostrarla u ocultarla. Recibe las props del <input> (value, onChange, autoComplete…)
 * y, dentro de un Campo, el id y los aria-* que este le pone.
 */
export default function CampoClave({ className = "", ...props }) {
  const [ver, setVer] = useState(false);
  return (
    <div className="ui-clave">
      <input {...props} type={ver ? "text" : "password"} className={`ui-field__input ${className}`} />
      <button type="button" className="ui-clave__ver" onClick={() => setVer((v) => !v)}
        aria-label={ver ? "Ocultar contraseña" : "Mostrar contraseña"} aria-pressed={ver} aria-controls={props.id}>
        <Icon name={ver ? "eyeOff" : "eye"} size={18} />
      </button>
    </div>
  );
}
