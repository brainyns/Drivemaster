import { Children, cloneElement, isValidElement } from "react";
import Icon from "../public/Icon";

/**
 * Campo de formulario: etiqueta asociada, ayuda y error en línea. Al control (el único hijo) le pone el id, y
 * aria-invalid y aria-describedby según la ayuda y el error, para que un lector de pantalla los lea al enfocarlo.
 */
export default function Campo({ id, label, opcional = false, ayuda, error, className = "", children }) {
  const idAyuda = ayuda ? `${id}-ayuda` : null;
  const idError = error ? `${id}-error` : null;
  const control = Children.only(children);
  const describe = [control.props?.["aria-describedby"], idAyuda, idError].filter(Boolean).join(" ") || undefined;
  const conAria = isValidElement(control)
    ? cloneElement(control, { id, "aria-invalid": error ? true : undefined, "aria-describedby": describe })
    : control;

  return (
    <div className={`ui-field ${className}`}>
      <label htmlFor={id} className="ui-field__label">
        {label}
        {opcional && <span className="ui-field__opcional">opcional</span>}
      </label>
      {conAria}
      {ayuda && <p id={idAyuda} className="ui-field__ayuda">{ayuda}</p>}
      {error && <p id={idError} className="ui-field__error"><Icon name="alert" size={14} />{error}</p>}
    </div>
  );
}
