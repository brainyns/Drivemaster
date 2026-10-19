import Icon from "../public/Icon";

/**
 * "Así te ven los clientes": el contenido (children) es la misma pieza que ve el cliente, dentro de un marco solo
 * visual (aria-hidden, los datos ya están en las secciones). consejo va debajo; con tono "aviso" se destaca.
 */
export default function VistaPrevia({
  id, titulo = "Así te ven los clientes", ayuda, consejo, tonoConsejo, iconoConsejo = "pin", children,
}) {
  const idTitulo = `${id}-titulo`;
  return (
    <section className="ui-vista" aria-labelledby={idTitulo}>
      <h2 id={idTitulo} className="ui-vista__titulo">{titulo}</h2>
      {ayuda && <p className="ui-vista__ayuda">{ayuda}</p>}
      <div className="ui-vista__marco" aria-hidden="true">{children}</div>
      {consejo && (
        <p className={`ui-vista__consejo${tonoConsejo === "aviso" ? " ui-vista__consejo--aviso" : ""}`}>
          <Icon name={tonoConsejo === "aviso" ? "alert" : iconoConsejo} size={16} /> {consejo}
        </p>
      )}
    </section>
  );
}
