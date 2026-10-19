import Icon from "../public/Icon";

/**
 * Cabecera de una pantalla: "Volver" (opcional) encima del título, una descripción breve y, a la derecha, la
 * acción principal. children va debajo (p. ej. los pasos).
 */
export default function EncabezadoPagina({ titulo, descripcion, onVolver, textoVolver = "Volver", acciones, idTitulo, children }) {
  return (
    <header className="ui-page-header">
      {onVolver && (
        <button type="button" className="ui-volver" onClick={onVolver}>
          <Icon name="arrow" size={16} className="ui-volver__icono" />
          {textoVolver}
        </button>
      )}
      <div className="ui-page-header__fila">
        <div className="ui-page-header__texto">
          <h1 className="ui-page-header__titulo" id={idTitulo}>{titulo}</h1>
          {descripcion && <p className="ui-page-header__sub">{descripcion}</p>}
        </div>
        {acciones && <div className="ui-page-header__acciones">{acciones}</div>}
      </div>
      {children}
    </header>
  );
}
