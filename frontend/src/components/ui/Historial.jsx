/**
 * Historial o línea de tiempo genérica, del más reciente al más antiguo: cada paso con su título, la fecha (ya
 * formateada en `cuando`, y la original en `fecha` para <time>) y un detalle opcional. tono marca un paso que salió
 * mal ("error") o el que está en curso ("actual"; por defecto, el más reciente).
 */
export default function Historial({ pasos = [], etiqueta = "Historial" }) {
  if (!pasos.length) return null;
  return (
    <ol className="ui-historial" aria-label={etiqueta}>
      {pasos.map((p, i) => (
        <li key={`${p.fecha}-${p.titulo}`}
          className={`ui-historial__paso${i === 0 ? " ui-historial__paso--actual" : ""}${p.tono === "error" ? " ui-historial__paso--error" : ""}`}>
          <span className="ui-historial__marca" aria-hidden="true" />
          <div className="ui-historial__texto">
            <strong>{p.titulo}</strong>
            {p.cuando && <time dateTime={p.fecha}>{p.cuando}</time>}
            {p.detalle && <p className="ui-historial__detalle">{p.detalle}</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}
