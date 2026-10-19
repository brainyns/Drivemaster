/**
 * Marcador de carga con la forma del contenido que viene: "lista" (filas), "tarjetas" (rejilla con imagen) o
 * "formulario" (etiqueta + campo). El texto solo lo leen los lectores de pantalla.
 */
export default function Esqueleto({ variante = "lista", cantidad = 3, texto = "Cargando…" }) {
  const items = Array.from({ length: cantidad }, (_, i) => i);
  return (
    <div aria-busy="true">
      <p className="ui-sr" role="status">{texto}</p>
      <div className={`ui-esqueleto ui-esqueleto--${variante}`} aria-hidden="true">
        {variante === "tarjetas" && items.map((i) => (
          <div key={i} className="ui-esqueleto__tarjeta">
            <span className="ui-skeleton ui-esqueleto__media" />
            <span className="ui-skeleton ui-esqueleto__titulo" />
            <span className="ui-skeleton ui-esqueleto__linea" />
          </div>
        ))}
        {variante === "formulario" && items.map((i) => (
          <div key={i} className="ui-esqueleto__campo">
            <span className="ui-skeleton ui-esqueleto__etiqueta" />
            <span className="ui-skeleton ui-esqueleto__control" />
          </div>
        ))}
        {variante === "lista" && items.map((i) => (
          <div key={i} className="ui-esqueleto__fila">
            <span className="ui-skeleton ui-esqueleto__titulo" />
            <span className="ui-skeleton ui-esqueleto__linea" />
            <span className="ui-skeleton ui-esqueleto__linea ui-esqueleto__linea--corta" />
          </div>
        ))}
      </div>
    </div>
  );
}
