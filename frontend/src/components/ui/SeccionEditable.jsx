import { useEffect, useRef } from "react";
import Icon from "../public/Icon";

/**
 * Sección de un perfil que se edita por partes: título, una línea de ayuda y "Editar" a la derecha. Mientras
 * editando es true, children es el formulario (con su Guardar y Cancelar); si no, la vista de los datos. aviso va
 * bajo la cabecera (p. ej. "Cambios guardados"). Sin onEditar la sección es solo lectura; nota explica por qué.
 * Al cerrar la edición (guardar o cancelar) el foco vuelve a "Editar" para no perder el lugar con el teclado.
 */
export default function SeccionEditable({
  id, titulo, descripcion, icono, onEditar, editando = false, textoEditar = "Editar", nota, aviso, children,
}) {
  const idTitulo = `${id}-titulo`;
  const boton = useRef(null);
  const estabaEditando = useRef(editando);

  useEffect(() => {
    if (estabaEditando.current && !editando) boton.current?.focus();
    estabaEditando.current = editando;
  }, [editando]);

  return (
    <section id={id} className={`ui-seccion${editando ? " ui-seccion--editando" : ""}`} aria-labelledby={idTitulo}>
      <header className="ui-seccion__cabecera">
        {icono && <span className="ui-seccion__icono" aria-hidden="true"><Icon name={icono} size={18} /></span>}
        <div className="ui-seccion__texto">
          <h2 id={idTitulo} className="ui-seccion__titulo">{titulo}</h2>
          {descripcion && <p className="ui-seccion__desc">{descripcion}</p>}
        </div>
        {onEditar && !editando && (
          <button ref={boton} type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={onEditar}
            aria-describedby={idTitulo}>
            <Icon name="pencil" size={14} /> {textoEditar}
          </button>
        )}
      </header>
      {aviso}
      <div className="ui-seccion__cuerpo">{children}</div>
      {nota && <p className="ui-seccion__nota">{nota}</p>}
    </section>
  );
}
