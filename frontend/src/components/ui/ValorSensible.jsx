import { useState } from "react";
import Icon from "../public/Icon";
import { enmascarar } from "../../utils/formato";

/**
 * Cuenta bancaria o documento: se ve enmascarado ("•••• 4521") y "Mostrar" lo deja ver completo hasta volver a
 * ocultarlo. Así no queda a la vista de quien mira la pantalla (por ejemplo, en la feria). prefijo: lo que no se oculta
 * (el tipo de documento).
 */
export default function ValorSensible({ valor, prefijo = null, etiqueta }) {
  const [visible, setVisible] = useState(false);
  if (!valor) return null;
  return (
    <span className="ui-sensible">
      <span className="ui-sensible__valor">
        {prefijo ? `${prefijo} ` : ""}{visible ? valor : enmascarar(valor)}
      </span>
      <button type="button" className="ui-sensible__boton" onClick={() => setVisible((v) => !v)}
        aria-pressed={visible} aria-label={`${visible ? "Ocultar" : "Mostrar"} ${etiqueta}`}>
        <Icon name={visible ? "eyeOff" : "eye"} size={16} />
        <span>{visible ? "Ocultar" : "Mostrar"}</span>
      </button>
    </span>
  );
}
