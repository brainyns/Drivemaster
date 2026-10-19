import Icon from "../public/Icon";

/**
 * Indicador de pasos con el actual resaltado. pasos: nombres; actual: número desde 1. Con onIr, los pasos ya
 * hechos se pueden volver a abrir. En celular solo se ven las marcas y el texto "Paso X de N: nombre".
 */
export default function Pasos({ pasos, actual, onIr, id = "ui-pasos" }) {
  const total = pasos.length;
  return (
    <div className="ui-pasos" style={{ "--ui-pasos": total }}>
      <p className="ui-pasos__texto" id={`${id}-texto`}>
        Paso {actual} de {total}: <strong>{pasos[actual - 1]}</strong>
      </p>
      <ol className="ui-pasos__lista" aria-labelledby={`${id}-texto`}>
        {pasos.map((nombre, i) => {
          const n = i + 1;
          const estado = n < actual ? "hecho" : n === actual ? "actual" : "pendiente";
          const contenido = (
            <>
              <span className="ui-pasos__marca" aria-hidden="true">
                {estado === "hecho" ? <Icon name="check" size={12} /> : n}
              </span>
              <span className="ui-pasos__nombre">{nombre}</span>
              <span className="ui-sr">{estado === "hecho" ? " (completado)" : estado === "actual" ? " (paso actual)" : ""}</span>
            </>
          );
          return (
            <li key={nombre} className={`ui-pasos__paso ui-pasos__paso--${estado}`} aria-current={n === actual ? "step" : undefined}>
              {onIr && estado === "hecho"
                ? <button type="button" className="ui-pasos__ir" onClick={() => onIr(n)}>{contenido}</button>
                : contenido}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
