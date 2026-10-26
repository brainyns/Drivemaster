import Icon from "../public/Icon";

/** Estado vacío que enseña qué hacer: ícono, título, una línea y la acción (children). */
export default function EstadoVacio({ icono = "box", titulo, texto, nivel = 2, compacto = false, children }) {
  const Titulo = `h${nivel}`;
  return (
    <div className={`ui-vacio${compacto ? " ui-vacio--compacto" : ""}`}>
      <span className="ui-vacio__icono" aria-hidden="true"><Icon name={icono} size={28} /></span>
      <Titulo className="ui-vacio__titulo">{titulo}</Titulo>
      {texto && <p className="ui-vacio__texto">{texto}</p>}
      {children && <div className="ui-vacio__acciones">{children}</div>}
    </div>
  );
}
