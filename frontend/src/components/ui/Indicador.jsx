import Icon from "../public/Icon";

/**
 * Cifra de un resumen (panel, informes): nombre, valor y una línea que lo explica. tono "accion" marca lo que pide
 * atención con el acento; "ok", lo que va bien. Con onClick es un botón que lleva a la lista de esa cifra.
 */
export default function Indicador({ titulo, valor, pie, tono, icono, onClick }) {
  const Raiz = onClick ? "button" : "div";
  return (
    <Raiz type={onClick ? "button" : undefined} onClick={onClick}
      className={`ui-indicador${tono ? ` ui-indicador--${tono}` : ""}${onClick ? " ui-indicador--enlace" : ""}`}>
      <span className="ui-indicador__titulo">
        {icono && <Icon name={icono} size={16} />}
        {titulo}
      </span>
      <span className="ui-indicador__valor">{valor}</span>
      {pie && <span className="ui-indicador__pie">{pie}</span>}
      {onClick && <Icon name="arrow" size={16} className="ui-indicador__flecha" />}
    </Raiz>
  );
}
