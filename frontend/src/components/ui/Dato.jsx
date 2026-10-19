/** Un dato de solo lectura dentro de un <dl className="ui-datos">; sin valor muestra "Sin registrar". */
export default function Dato({ etiqueta, ancho = false, children }) {
  const vacio = children == null || children === "" || children === false || (Array.isArray(children) && children.length === 0);
  return (
    <div className={`ui-datos__item${ancho ? " ui-datos__item--ancho" : ""}`}>
      <dt>{etiqueta}</dt>
      <dd>{vacio ? <span className="ui-datos__falta">Sin registrar</span> : children}</dd>
    </div>
  );
}
